package com.zappyware.cameratest

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import com.zappyware.cameratest.databinding.ActivityCameraBinding
import com.zappyware.cameratest.ui.fragment.CameraFragment
import com.zappyware.cameratest.ui.fragment.PreviewFragment

class CameraActivity : AppCompatActivity() {

    private lateinit var viewModel: CameraViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this).get(CameraViewModel::class.java)

        val binding: ActivityCameraBinding = DataBindingUtil.setContentView(this, R.layout.activity_camera)
        binding.executePendingBindings()

        viewModel.viewInteractor.observe(this) {
            when(it) {
                CameraViewModel.CameraViewInteractor.NavigateBack ->
                    onBackPressed()
                CameraViewModel.CameraViewInteractor.PreviewFile ->
                    supportFragmentManager.beginTransaction()
                        .addToBackStack(null)
                        .replace(binding.content.id, PreviewFragment.newInstance())
                        .commit()
            }
        }

        if(binding.content.childCount == 0) {
            supportFragmentManager.beginTransaction()
                .add(binding.content.id, CameraFragment.newInstance())
                .commit()
        }
    }
}
