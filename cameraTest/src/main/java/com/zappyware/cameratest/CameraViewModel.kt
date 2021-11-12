package com.zappyware.cameratest

import androidx.lifecycle.ViewModel
import com.zappyware.cameratest.utils.BaseViewInteractor
import com.zappyware.cameratest.utils.ViewInteractorLiveData
import java.io.File

class CameraViewModel: ViewModel() {

    val viewInteractor = ViewInteractorLiveData<CameraViewInteractor>()

    var currentPhotoFile: File? = null

    private fun CameraViewInteractor.interact() {
        viewInteractor.value = this
    }

    fun takePicture() {
        CameraViewInteractor.TakePicture.interact()
    }

    fun previewFile() {
        CameraViewInteractor.PreviewFile.interact()
    }

    fun navigateBack() {
        CameraViewInteractor.NavigateBack.interact()
    }

    sealed class CameraViewInteractor : BaseViewInteractor() {
        object TakePicture : CameraViewInteractor()
        object NavigateBack : CameraViewInteractor()
        object PreviewFile : CameraViewInteractor()
    }
}
