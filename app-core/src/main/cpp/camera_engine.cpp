#include "camera_engine.h"
#include <algorithm>
#include <cstring>

// Static device callback forwarders
static void OnDeviceDisconnectedForwarder(void* context, ACameraDevice* device) {
    if (context) {
        reinterpret_cast<CameraEngine*>(context)->OnDeviceDisconnected(device);
    }
}

static void OnDeviceErrorForwarder(void* context, ACameraDevice* device, int error) {
    if (context) {
        reinterpret_cast<CameraEngine*>(context)->OnDeviceError(device, error);
    }
}

// Static session callback forwarders
static void OnSessionClosedForwarder(void* context, ACameraCaptureSession* session) {
    if (context) {
        reinterpret_cast<CameraEngine*>(context)->OnSessionClosed(session);
    }
}

static void OnSessionReadyForwarder(void* context, ACameraCaptureSession* session) {
    if (context) {
        reinterpret_cast<CameraEngine*>(context)->OnSessionReady(session);
    }
}

static void OnSessionActiveForwarder(void* context, ACameraCaptureSession* session) {
    if (context) {
        reinterpret_cast<CameraEngine*>(context)->OnSessionActive(session);
    }
}

// Static capture callback forwarders
static void OnCaptureStartedForwarder(void* context, ACameraCaptureSession* session, const ACaptureRequest* request, int64_t timestamp) {
    if (context) {
        reinterpret_cast<CameraEngine*>(context)->OnCaptureStarted(session, request, timestamp);
    }
}

static void OnCaptureCompletedForwarder(void* context, ACameraCaptureSession* session, ACaptureRequest* request, const ACameraMetadata* result) {
    if (context) {
        reinterpret_cast<CameraEngine*>(context)->OnCaptureCompleted(session, request, result);
    }
}

static void OnCaptureFailedForwarder(void* context, ACameraCaptureSession* session, ACaptureRequest* request, ACameraCaptureFailure* failure) {
    if (context) {
        reinterpret_cast<CameraEngine*>(context)->OnCaptureFailed(session, request, failure);
    }
}

// Static image listener forwarder
static void OnImageAvailableForwarder(void* context, AImageReader* reader) {
    if (context) {
        reinterpret_cast<CameraEngine*>(context)->OnImageAvailable(reader);
    }
}

CameraEngine::CameraEngine() {
    mLastFpsCalcTime = std::chrono::steady_clock::now();
}

CameraEngine::~CameraEngine() {
    Shutdown();
}

bool CameraEngine::Initialize() {
    std::lock_guard<std::mutex> lock(mMutex);
    if (!mCameraManager) {
        mCameraManager = ACameraManager_create();
        if (!mCameraManager) {
            LOGE("Failed to create ACameraManager");
            return false;
        }
        LOGI("ACameraManager successfully initialized");
    }
    return true;
}

void CameraEngine::Shutdown() {
    CloseCamera();
    std::lock_guard<std::mutex> lock(mMutex);
    if (mCameraManager) {
        ACameraManager_delete(mCameraManager);
        mCameraManager = nullptr;
    }
}

std::vector<std::string> CameraEngine::GetCameraIdList() {
    std::lock_guard<std::mutex> lock(mMutex);
    std::vector<std::string> idList;
    if (!mCameraManager) {
        LOGE("GetCameraIdList called before manager initialized");
        return idList;
    }

    ACameraIdList* cameras = nullptr;
    camera_status_t status = ACameraManager_getCameraIdList(mCameraManager, &cameras);
    if (status != ACAMERA_OK || !cameras) {
        LOGE("Failed to get camera ID list, status: %d", status);
        return idList;
    }

    for (int i = 0; i < cameras->numCameras; ++i) {
        idList.emplace_back(cameras->cameraIds[i]);
    }
    ACameraManager_deleteCameraIdList(cameras);
    return idList;
}

bool CameraEngine::GetCameraCharacteristics(const std::string& cameraId, NativeCameraMetadataInfo& outInfo) {
    std::lock_guard<std::mutex> lock(mMutex);
    if (!mCameraManager) return false;

    ACameraMetadata* chars = nullptr;
    camera_status_t status = ACameraManager_getCameraCharacteristics(mCameraManager, cameraId.c_str(), &chars);
    if (status != ACAMERA_OK || !chars) {
        LOGE("Failed to get characteristics for camera %s, status: %d", cameraId.c_str(), status);
        return false;
    }

    outInfo.cameraId = cameraId;

    // Lens facing
    ACameraMetadata_const_entry entry;
    if (ACameraMetadata_getConstEntry(chars, ACAMERA_LENS_FACING, &entry) == ACAMERA_OK && entry.count > 0) {
        outInfo.facing = entry.data.u8[0];
    }

    // Hardware Level
    if (ACameraMetadata_getConstEntry(chars, ACAMERA_INFO_SUPPORTED_HARDWARE_LEVEL, &entry) == ACAMERA_OK && entry.count > 0) {
        outInfo.hardwareLevel = entry.data.u8[0];
    }

    // Sensor Orientation
    if (ACameraMetadata_getConstEntry(chars, ACAMERA_SENSOR_ORIENTATION, &entry) == ACAMERA_OK && entry.count > 0) {
        outInfo.sensorOrientation = entry.data.i32[0];
    }

    // Active Array Size
    if (ACameraMetadata_getConstEntry(chars, ACAMERA_SENSOR_INFO_ACTIVE_ARRAY_SIZE, &entry) == ACAMERA_OK && entry.count >= 4) {
        mActiveArrayWidth = entry.data.i32[2] - entry.data.i32[0];
        mActiveArrayHeight = entry.data.i32[3] - entry.data.i32[1];
    }

    // Flash Available
    if (ACameraMetadata_getConstEntry(chars, ACAMERA_FLASH_INFO_AVAILABLE, &entry) == ACAMERA_OK && entry.count > 0) {
        outInfo.hasFlash = (entry.data.u8[0] != 0);
    }

    // Sensitivity / ISO Range
    if (ACameraMetadata_getConstEntry(chars, ACAMERA_SENSOR_INFO_SENSITIVITY_RANGE, &entry) == ACAMERA_OK && entry.count >= 2) {
        outInfo.minIso = entry.data.i32[0];
        outInfo.maxIso = entry.data.i32[1];
    }

    // Exposure Time Range
    if (ACameraMetadata_getConstEntry(chars, ACAMERA_SENSOR_INFO_EXPOSURE_TIME_RANGE, &entry) == ACAMERA_OK && entry.count >= 2) {
        outInfo.minExposureTimeNs = entry.data.i64[0];
        outInfo.maxExposureTimeNs = entry.data.i64[1];
    }

    // Minimum Focus Distance
    if (ACameraMetadata_getConstEntry(chars, ACAMERA_LENS_INFO_MINIMUM_FOCUS_DISTANCE, &entry) == ACAMERA_OK && entry.count > 0) {
        outInfo.minFocusDistance = entry.data.f[0];
    }

    // Max Digital Zoom
    if (ACameraMetadata_getConstEntry(chars, ACAMERA_SCALER_AVAILABLE_MAX_DIGITAL_ZOOM, &entry) == ACAMERA_OK && entry.count > 0) {
        outInfo.maxDigitalZoom = entry.data.f[0];
    }

    // Available AF Modes
    if (ACameraMetadata_getConstEntry(chars, ACAMERA_CONTROL_AF_AVAILABLE_MODES, &entry) == ACAMERA_OK) {
        for (uint32_t i = 0; i < entry.count; ++i) {
            outInfo.supportedAfModes.push_back(entry.data.u8[i]);
        }
    }

    // Available AE Modes
    if (ACameraMetadata_getConstEntry(chars, ACAMERA_CONTROL_AE_AVAILABLE_MODES, &entry) == ACAMERA_OK) {
        for (uint32_t i = 0; i < entry.count; ++i) {
            outInfo.supportedAeModes.push_back(entry.data.u8[i]);
        }
    }

    // Available AWB Modes
    if (ACameraMetadata_getConstEntry(chars, ACAMERA_CONTROL_AWB_AVAILABLE_MODES, &entry) == ACAMERA_OK) {
        for (uint32_t i = 0; i < entry.count; ++i) {
            outInfo.supportedAwbModes.push_back(entry.data.u8[i]);
        }
    }

    // Optical Stabilization
    if (ACameraMetadata_getConstEntry(chars, ACAMERA_LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION, &entry) == ACAMERA_OK) {
        for (uint32_t i = 0; i < entry.count; ++i) {
            if (entry.data.u8[i] == ACAMERA_LENS_OPTICAL_STABILIZATION_MODE_ON) {
                outInfo.supportsOis = true;
                break;
            }
        }
    }

    // Stream Configurations (Resolutions for Preview & JPEG)
    if (ACameraMetadata_getConstEntry(chars, ACAMERA_SCALER_AVAILABLE_STREAM_CONFIGURATIONS, &entry) == ACAMERA_OK) {
        for (uint32_t i = 0; i + 3 < entry.count; i += 4) {
            int32_t format = entry.data.i32[i];
            int32_t width = entry.data.i32[i + 1];
            int32_t height = entry.data.i32[i + 2];
            int32_t isInput = entry.data.i32[i + 3];

            if (isInput == 0) { // Output streams only
                if (format == 0x100) { // AIMAGE_FORMAT_JPEG
                    outInfo.supportedJpegWidths.push_back(width);
                    outInfo.supportedJpegHeights.push_back(height);
                } else if (format == 35 || format == 34) { // YUV_420_888 or PRIVATE
                    outInfo.supportedPreviewWidths.push_back(width);
                    outInfo.supportedPreviewHeights.push_back(height);
                }
            }
        }
    }

    ACameraMetadata_free(chars);
    return true;
}

bool CameraEngine::OpenCamera(const std::string& cameraId) {
    std::lock_guard<std::mutex> lock(mMutex);
    if (!mCameraManager) return false;

    if (mCameraDevice) {
        CloseCamera();
    }

    ACameraDevice_StateCallbacks deviceCallbacks = {
        .context = this,
        .onDisconnected = OnDeviceDisconnectedForwarder,
        .onError = OnDeviceErrorForwarder
    };

    camera_status_t status = ACameraManager_openCamera(mCameraManager, cameraId.c_str(), &deviceCallbacks, &mCameraDevice);
    if (status != ACAMERA_OK || !mCameraDevice) {
        LOGE("Failed to open camera %s, status: %d", cameraId.c_str(), status);
        return false;
    }

    mActiveCameraId = cameraId;
    ResetStats();
    LOGI("Camera %s successfully opened (device: %p)", cameraId.c_str(), mCameraDevice);
    return true;
}

bool CameraEngine::SetPreviewSurface(ANativeWindow* window, int32_t width, int32_t height) {
    std::lock_guard<std::mutex> lock(mMutex);
    if (mPreviewWindow && mPreviewWindow != window) {
        ANativeWindow_release(mPreviewWindow);
        mPreviewWindow = nullptr;
    }

    if (window) {
        mPreviewWindow = window;
        // Keep window reference from ANativeWindow_fromSurface
        // DO NOT call ANativeWindow_setBuffersGeometry with forced RGBA_8888,
        // because camera stream needs HAL-negotiated format!
        LOGI("Preview ANativeWindow configured: %p", mPreviewWindow);
        return true;
    }
    return false;
}

bool CameraEngine::CreateCaptureSession(int32_t previewWidth, int32_t previewHeight, int32_t captureWidth, int32_t captureHeight) {
    std::lock_guard<std::mutex> lock(mMutex);
    if (!mCameraDevice || !mPreviewWindow) {
        LOGE("CreateCaptureSession: CameraDevice (%p) or PreviewWindow (%p) is null", mCameraDevice, mPreviewWindow);
        return false;
    }

    // Clean up existing session
    if (mCaptureSession) {
        ACameraCaptureSession_stopRepeating(mCaptureSession);
        ACameraCaptureSession_abortCaptures(mCaptureSession);
        ACameraCaptureSession_close(mCaptureSession);
        mCaptureSession = nullptr;
    }
    if (mPreviewRequest) {
        ACaptureRequest_free(mPreviewRequest);
        mPreviewRequest = nullptr;
    }
    if (mStillRequest) {
        ACaptureRequest_free(mStillRequest);
        mStillRequest = nullptr;
    }
    if (mOutputContainer) {
        ACaptureSessionOutputContainer_free(mOutputContainer);
        mOutputContainer = nullptr;
    }
    if (mPreviewSessionOutput) {
        ACaptureSessionOutput_free(mPreviewSessionOutput);
        mPreviewSessionOutput = nullptr;
    }
    if (mImageReaderSessionOutput) {
        ACaptureSessionOutput_free(mImageReaderSessionOutput);
        mImageReaderSessionOutput = nullptr;
    }
    if (mPreviewTarget) {
        ACameraOutputTarget_free(mPreviewTarget);
        mPreviewTarget = nullptr;
    }
    if (mImageReaderTarget) {
        ACameraOutputTarget_free(mImageReaderTarget);
        mImageReaderTarget = nullptr;
    }
    if (mImageReader) {
        AImageReader_delete(mImageReader);
        mImageReader = nullptr;
        mImageReaderWindow = nullptr;
    }

    camera_status_t status = ACaptureSessionOutputContainer_create(&mOutputContainer);
    if (status != ACAMERA_OK || !mOutputContainer) {
        LOGE("Failed to create session output container: %d", status);
        return false;
    }

    // 1. Add Preview Output
    status = ACaptureSessionOutput_create(mPreviewWindow, &mPreviewSessionOutput);
    if (status != ACAMERA_OK || !mPreviewSessionOutput) {
        LOGE("Failed to create preview session output: %d", status);
        return false;
    }
    ACaptureSessionOutputContainer_add(mOutputContainer, mPreviewSessionOutput);
    ACameraOutputTarget_create(mPreviewWindow, &mPreviewTarget);

    // 2. Add ImageReader (only if valid capture dimensions given)
    if (captureWidth > 0 && captureHeight > 0) {
        media_status_t mediaStatus = AImageReader_new(captureWidth, captureHeight, AIMAGE_FORMAT_JPEG, 2, &mImageReader);
        if (mediaStatus == AMEDIA_OK && mImageReader) {
            AImageReader_ImageListener listener = {
                .context = this,
                .onImageAvailable = OnImageAvailableForwarder
            };
            AImageReader_setImageListener(mImageReader, &listener);
            AImageReader_getWindow(mImageReader, &mImageReaderWindow);

            if (mImageReaderWindow) {
                if (ACaptureSessionOutput_create(mImageReaderWindow, &mImageReaderSessionOutput) == ACAMERA_OK) {
                    ACaptureSessionOutputContainer_add(mOutputContainer, mImageReaderSessionOutput);
                    ACameraOutputTarget_create(mImageReaderWindow, &mImageReaderTarget);
                }
            }
        }
    }

    // 3. Create Capture Session with callbacks
    ACameraCaptureSession_stateCallbacks sessionCallbacks = {
        .context = this,
        .onClosed = OnSessionClosedForwarder,
        .onReady = OnSessionReadyForwarder,
        .onActive = OnSessionActiveForwarder
    };

    status = ACameraDevice_createCaptureSession(mCameraDevice, mOutputContainer, &sessionCallbacks, &mCaptureSession);
    if (status != ACAMERA_OK || !mCaptureSession) {
        LOGW("Dual-output capture session failed (%d). Retrying with preview-only output...", status);
        // Fallback: Preview only output
        if (mImageReaderSessionOutput) {
            ACaptureSessionOutputContainer_remove(mOutputContainer, mImageReaderSessionOutput);
        }
        status = ACameraDevice_createCaptureSession(mCameraDevice, mOutputContainer, &sessionCallbacks, &mCaptureSession);
        if (status != ACAMERA_OK || !mCaptureSession) {
            LOGE("Fallback preview-only session also failed: %d", status);
            return false;
        }
    }

    LOGI("ACameraCaptureSession successfully created!");
    return true;
}

bool CameraEngine::StartStreaming() {
    std::lock_guard<std::mutex> lock(mMutex);
    if (!mCameraDevice || !mCaptureSession || !mPreviewTarget) {
        LOGE("StartStreaming: Required resources not initialized (device=%p, session=%p, target=%p)",
             mCameraDevice, mCaptureSession, mPreviewTarget);
        return false;
    }

    if (mPreviewRequest) {
        ACaptureRequest_free(mPreviewRequest);
        mPreviewRequest = nullptr;
    }

    camera_status_t status = ACameraDevice_createCaptureRequest(mCameraDevice, TEMPLATE_PREVIEW, &mPreviewRequest);
    if (status != ACAMERA_OK || !mPreviewRequest) {
        LOGE("Failed to create TEMPLATE_PREVIEW request: %d", status);
        return false;
    }

    ACaptureRequest_addTarget(mPreviewRequest, mPreviewTarget);
    ApplySettingsToRequest(mPreviewRequest, mCurrentSettings);

    ACameraCaptureSession_captureCallbacks captureCallbacks = {
        .context = this,
        .onCaptureStarted = OnCaptureStartedForwarder,
        .onCaptureProgressed = nullptr,
        .onCaptureCompleted = OnCaptureCompletedForwarder,
        .onCaptureFailed = OnCaptureFailedForwarder,
        .onCaptureSequenceCompleted = nullptr,
        .onCaptureSequenceAborted = nullptr,
        .onCaptureBufferLost = nullptr
    };

    int sequenceId = 0;
    status = ACameraCaptureSession_setRepeatingRequest(mCaptureSession, &captureCallbacks, 1, &mPreviewRequest, &sequenceId);
    if (status != ACAMERA_OK) {
        LOGE("Failed to set repeating preview request: %d", status);
        return false;
    }

    mIsStreaming = true;
    mLastFpsCalcTime = std::chrono::steady_clock::now();
    mFramesSinceLastCalc = 0;
    LOGI("Streaming started with sequenceId: %d", sequenceId);
    return true;
}

bool CameraEngine::StopStreaming() {
    std::lock_guard<std::mutex> lock(mMutex);
    if (mCaptureSession) {
        ACameraCaptureSession_stopRepeating(mCaptureSession);
        ACameraCaptureSession_abortCaptures(mCaptureSession);
    }
    mIsStreaming = false;
    LOGI("Streaming stopped");
    return true;
}

bool CameraEngine::UpdateControls(const NativeControlSettings& settings) {
    std::lock_guard<std::mutex> lock(mMutex);
    mCurrentSettings = settings;

    if (!mCaptureSession || !mPreviewRequest || !mIsStreaming) {
        return true;
    }

    ApplySettingsToRequest(mPreviewRequest, mCurrentSettings);

    ACameraCaptureSession_captureCallbacks captureCallbacks = {
        .context = this,
        .onCaptureStarted = OnCaptureStartedForwarder,
        .onCaptureProgressed = nullptr,
        .onCaptureCompleted = OnCaptureCompletedForwarder,
        .onCaptureFailed = OnCaptureFailedForwarder,
        .onCaptureSequenceCompleted = nullptr,
        .onCaptureSequenceAborted = nullptr,
        .onCaptureBufferLost = nullptr
    };

    int sequenceId = 0;
    camera_status_t status = ACameraCaptureSession_setRepeatingRequest(mCaptureSession, &captureCallbacks, 1, &mPreviewRequest, &sequenceId);
    return (status == ACAMERA_OK);
}

void CameraEngine::ApplySettingsToRequest(ACaptureRequest* request, const NativeControlSettings& settings) {
    if (!request) return;

    // AE Mode
    uint8_t aeMode = static_cast<uint8_t>(settings.aeMode);
    ACaptureRequest_setEntry_u8(request, ACAMERA_CONTROL_AE_MODE, 1, &aeMode);

    // Manual Exposure & ISO
    if (settings.aeMode == ACAMERA_CONTROL_AE_MODE_OFF) {
        if (settings.exposureTimeNs > 0) {
            int64_t exp = settings.exposureTimeNs;
            ACaptureRequest_setEntry_i64(request, ACAMERA_SENSOR_EXPOSURE_TIME, 1, &exp);
        }
        if (settings.iso > 0) {
            int32_t sens = settings.iso;
            ACaptureRequest_setEntry_i32(request, ACAMERA_SENSOR_SENSITIVITY, 1, &sens);
        }
    } else {
        int32_t expComp = settings.exposureCompensation;
        ACaptureRequest_setEntry_i32(request, ACAMERA_CONTROL_AE_EXPOSURE_COMPENSATION, 1, &expComp);
    }

    // AF Mode
    uint8_t afMode = static_cast<uint8_t>(settings.afMode);
    ACaptureRequest_setEntry_u8(request, ACAMERA_CONTROL_AF_MODE, 1, &afMode);

    // Manual Focus Distance
    if (settings.afMode == ACAMERA_CONTROL_AF_MODE_OFF && settings.focusDistance >= 0.0f) {
        float fd = settings.focusDistance;
        ACaptureRequest_setEntry_float(request, ACAMERA_LENS_FOCUS_DISTANCE, 1, &fd);
    }

    // AWB Mode
    uint8_t awbMode = static_cast<uint8_t>(settings.awbMode);
    ACaptureRequest_setEntry_u8(request, ACAMERA_CONTROL_AWB_MODE, 1, &awbMode);

    // Flash Mode
    uint8_t flashMode = static_cast<uint8_t>(settings.flashMode);
    ACaptureRequest_setEntry_u8(request, ACAMERA_FLASH_MODE, 1, &flashMode);

    // Zoom Ratio (API 30+) and Scaler Crop Region (API 24+)
    float zoom = std::max(1.0f, settings.zoomRatio);
    ACaptureRequest_setEntry_float(request, ACAMERA_CONTROL_ZOOM_RATIO, 1, &zoom);

    if (mActiveArrayWidth > 0 && mActiveArrayHeight > 0) {
        int32_t cropW = static_cast<int32_t>(static_cast<float>(mActiveArrayWidth) / zoom);
        int32_t cropH = static_cast<int32_t>(static_cast<float>(mActiveArrayHeight) / zoom);
        int32_t cropLeft = (mActiveArrayWidth - cropW) / 2;
        int32_t cropTop = (mActiveArrayHeight - cropH) / 2;
        int32_t cropRegion[4] = { cropLeft, cropTop, cropLeft + cropW, cropTop + cropH };
        ACaptureRequest_setEntry_i32(request, ACAMERA_SCALER_CROP_REGION, 4, cropRegion);
    }

    // Optical Image Stabilization (OIS)
    uint8_t oisMode = settings.oisEnabled ? ACAMERA_LENS_OPTICAL_STABILIZATION_MODE_ON : ACAMERA_LENS_OPTICAL_STABILIZATION_MODE_OFF;
    ACaptureRequest_setEntry_u8(request, ACAMERA_LENS_OPTICAL_STABILIZATION_MODE, 1, &oisMode);
}

bool CameraEngine::TriggerAf(bool cancel) {
    std::lock_guard<std::mutex> lock(mMutex);
    if (!mCaptureSession || !mPreviewRequest) return false;

    uint8_t trigger = cancel ? ACAMERA_CONTROL_AF_TRIGGER_CANCEL : ACAMERA_CONTROL_AF_TRIGGER_START;
    ACaptureRequest_setEntry_u8(mPreviewRequest, ACAMERA_CONTROL_AF_TRIGGER, 1, &trigger);

    int sequenceId = 0;
    camera_status_t status = ACameraCaptureSession_capture(mCaptureSession, nullptr, 1, &mPreviewRequest, &sequenceId);

    // Revert trigger in repeating request
    uint8_t idleTrigger = ACAMERA_CONTROL_AF_TRIGGER_IDLE;
    ACaptureRequest_setEntry_u8(mPreviewRequest, ACAMERA_CONTROL_AF_TRIGGER, 1, &idleTrigger);
    return (status == ACAMERA_OK);
}

bool CameraEngine::CaptureStill() {
    std::lock_guard<std::mutex> lock(mMutex);
    if (!mCameraDevice || !mCaptureSession || !mImageReaderTarget) {
        LOGE("CaptureStill: Missing prerequisites for image capture (device=%p, session=%p, target=%p)",
             mCameraDevice, mCaptureSession, mImageReaderTarget);
        return false;
    }

    if (mStillRequest) {
        ACaptureRequest_free(mStillRequest);
        mStillRequest = nullptr;
    }

    camera_status_t status = ACameraDevice_createCaptureRequest(mCameraDevice, TEMPLATE_STILL_CAPTURE, &mStillRequest);
    if (status != ACAMERA_OK || !mStillRequest) {
        LOGE("Failed to create TEMPLATE_STILL_CAPTURE request: %d", status);
        return false;
    }

    ACaptureRequest_addTarget(mStillRequest, mImageReaderTarget);
    ApplySettingsToRequest(mStillRequest, mCurrentSettings);

    int sequenceId = 0;
    status = ACameraCaptureSession_capture(mCaptureSession, nullptr, 1, &mStillRequest, &sequenceId);
    if (status != ACAMERA_OK) {
        LOGE("Failed to capture still frame: %d", status);
        return false;
    }

    LOGI("Still capture submitted with sequenceId: %d", sequenceId);
    return true;
}

std::vector<uint8_t> CameraEngine::GetLatestCapturedImage() {
    std::lock_guard<std::mutex> lock(mMutex);
    mHasNewCapturedImage = false;
    return mLatestJpegBytes;
}

NativeCameraStats CameraEngine::GetCaptureStats() {
    std::lock_guard<std::mutex> lock(mMutex);
    return mStats;
}

void CameraEngine::CloseCamera() {
    std::lock_guard<std::mutex> lock(mMutex);
    mIsStreaming = false;

    if (mCaptureSession) {
        ACameraCaptureSession_stopRepeating(mCaptureSession);
        ACameraCaptureSession_abortCaptures(mCaptureSession);
        ACameraCaptureSession_close(mCaptureSession);
        mCaptureSession = nullptr;
    }

    if (mPreviewRequest) {
        ACaptureRequest_free(mPreviewRequest);
        mPreviewRequest = nullptr;
    }
    if (mStillRequest) {
        ACaptureRequest_free(mStillRequest);
        mStillRequest = nullptr;
    }

    if (mOutputContainer) {
        ACaptureSessionOutputContainer_free(mOutputContainer);
        mOutputContainer = nullptr;
    }
    if (mPreviewSessionOutput) {
        ACaptureSessionOutput_free(mPreviewSessionOutput);
        mPreviewSessionOutput = nullptr;
    }
    if (mImageReaderSessionOutput) {
        ACaptureSessionOutput_free(mImageReaderSessionOutput);
        mImageReaderSessionOutput = nullptr;
    }
    if (mPreviewTarget) {
        ACameraOutputTarget_free(mPreviewTarget);
        mPreviewTarget = nullptr;
    }
    if (mImageReaderTarget) {
        ACameraOutputTarget_free(mImageReaderTarget);
        mImageReaderTarget = nullptr;
    }

    if (mPreviewWindow) {
        ANativeWindow_release(mPreviewWindow);
        mPreviewWindow = nullptr;
    }
    if (mImageReader) {
        AImageReader_delete(mImageReader);
        mImageReader = nullptr;
        mImageReaderWindow = nullptr;
    }

    if (mCameraDevice) {
        ACameraDevice_close(mCameraDevice);
        mCameraDevice = nullptr;
    }

    mActiveCameraId.clear();
    LOGI("CameraEngine closed");
}

void CameraEngine::ResetStats() {
    mStats = NativeCameraStats();
    mFramesSinceLastCalc = 0;
    mLastFpsCalcTime = std::chrono::steady_clock::now();
}

// Callbacks
void CameraEngine::OnDeviceDisconnected(ACameraDevice* device) {
    LOGW("Camera device disconnected");
}

void CameraEngine::OnDeviceError(ACameraDevice* device, int error) {
    LOGE("Camera device error: %d", error);
}

void CameraEngine::OnSessionClosed(ACameraCaptureSession* session) {
    LOGI("Capture session closed");
}

void CameraEngine::OnSessionReady(ACameraCaptureSession* session) {
    LOGI("Capture session ready");
}

void CameraEngine::OnSessionActive(ACameraCaptureSession* session) {
    LOGI("Capture session active");
}

void CameraEngine::OnCaptureStarted(ACameraCaptureSession* session, const ACaptureRequest* request, int64_t timestamp) {
    std::lock_guard<std::mutex> lock(mMutex);
    mStats.lastTimestampNs = timestamp;
}

void CameraEngine::OnCaptureCompleted(ACameraCaptureSession* session, ACaptureRequest* request, const ACameraMetadata* result) {
    std::lock_guard<std::mutex> lock(mMutex);
    mStats.frameCount++;
    mFramesSinceLastCalc++;

    auto now = std::chrono::steady_clock::now();
    auto elapsedMs = std::chrono::duration_cast<std::chrono::milliseconds>(now - mLastFpsCalcTime).count();
    if (elapsedMs >= 1000) {
        mStats.fps = static_cast<float>(mFramesSinceLastCalc * 1000) / static_cast<float>(elapsedMs);
        mFramesSinceLastCalc = 0;
        mLastFpsCalcTime = now;
    }

    if (result) {
        ACameraMetadata_const_entry entry;
        if (ACameraMetadata_getConstEntry(result, ACAMERA_SENSOR_EXPOSURE_TIME, &entry) == ACAMERA_OK && entry.count > 0) {
            mStats.lastExposureDurationNs = entry.data.i64[0];
        }
        if (ACameraMetadata_getConstEntry(result, ACAMERA_SENSOR_SENSITIVITY, &entry) == ACAMERA_OK && entry.count > 0) {
            mStats.lastIso = entry.data.i32[0];
        }
    }
}

void CameraEngine::OnCaptureFailed(ACameraCaptureSession* session, ACaptureRequest* request, ACameraCaptureFailure* failure) {
    std::lock_guard<std::mutex> lock(mMutex);
    mStats.droppedFrames++;
    LOGW("Capture request failed");
}

void CameraEngine::OnImageAvailable(AImageReader* reader) {
    std::lock_guard<std::mutex> lock(mMutex);
    if (!reader) return;

    AImage* image = nullptr;
    media_status_t status = AImageReader_acquireLatestImage(reader, &image);
    if (status != AMEDIA_OK || !image) {
        return;
    }

    uint8_t* data = nullptr;
    int len = 0;
    if (AImage_getPlaneData(image, 0, &data, &len) == AMEDIA_OK && data && len > 0) {
        mLatestJpegBytes.assign(data, data + len);
        mHasNewCapturedImage = true;
        LOGI("Image reader captured JPEG of %d bytes", len);
    }
    AImage_delete(image);
}
