package com.zappyware.cameratest.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.zappyware.cameratest.CameraViewModel
import com.zappyware.cameratest.databinding.FragmentPreviewBinding

class PreviewFragment: BaseFragment() {

    companion object {

        @JvmStatic
        fun newInstance(): PreviewFragment = PreviewFragment()
    }

    private val viewModel: CameraViewModel by activityViewModels()

    private lateinit var binding: FragmentPreviewBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentPreviewBinding.inflate(inflater, container, false)
        binding.viewModel = viewModel
        binding.executePendingBindings()
        return binding.root
    }
}
