package com.zappyware.cameratest.utils

import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import androidx.collection.ArraySet
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer

class ViewInteractorLiveData<T> : MutableLiveData<T>() {

    private val observers = ArraySet<ObserverWrapper<in T>>()
    private val pendingInteractions = mutableListOf<T>()
    private val handler = Handler(Looper.getMainLooper())

    @MainThread
    override fun observe(owner: LifecycleOwner, observer: Observer<in T>) {
        val wrapper = ObserverWrapper(observer)
        observers.add(wrapper)
        super.observe(owner, wrapper)
        checkPendingInteractions()
    }

    @MainThread
    override fun observeForever(observer: Observer<in T>) {
        val wrapper = ObserverWrapper(observer)
        observers.add(wrapper)
        super.observeForever(wrapper)
        checkPendingInteractions()
    }

    @MainThread
    override fun removeObserver(observer: Observer<in T>) {
        if (observer is ObserverWrapper<*> && observers.remove(observer)) {
            super.removeObserver(observer)
            return
        }
        val iterator = observers.iterator()
        while (iterator.hasNext()) {
            val wrapper = iterator.next()
            if (wrapper.observer == observer) {
                iterator.remove()
                super.removeObserver(wrapper)
                break
            }
        }
    }

    override fun setValue(t: T?) {
        if (observers.isEmpty()) {
            t?.let { pendingInteractions.add(it) }
        } else {
            observers.forEach { it.allowNewValue() }
            if (isMainThread()) {
                super.setValue(t)
            } else {
                super.postValue(t)
            }
        }
    }

    override fun postValue(value: T) {
        throw IllegalAccessException("Use setValue() instead, it will handle background thread calls.")
    }

    private fun isMainThread(): Boolean {
        return sThreadCheckerDelegate?.isMainThread() ?: false
    }

    private fun checkPendingInteractions() {
        if (observers.isNotEmpty()) {
            pendingInteractions.forEach {
                handler.post { setValue(it) }
            }
            pendingInteractions.clear()
        }
    }

    companion object {
        var sThreadCheckerDelegate: ThreadCheckerDelegate? = null
            get() {
                if (field == null) {
                    field = object : ThreadCheckerDelegate {
                        override fun isMainThread(): Boolean {
                            return Looper.getMainLooper().thread == Thread.currentThread()
                        }
                    }
                }
                return field
            }
    }

    interface ThreadCheckerDelegate {
        fun isMainThread(): Boolean
    }

    private class ObserverWrapper<T>(val observer: Observer<T>) : Observer<T> {

        private var allow = 0

        override fun onChanged(t: T?) {
            if (allow > 0) {
                --allow
                observer.onChanged(t)
            }
        }

        fun allowNewValue() {
            ++allow
        }
    }
}
