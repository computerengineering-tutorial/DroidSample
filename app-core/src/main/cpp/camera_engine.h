#ifndef CAMERA_ENGINE_H
#define CAMERA_ENGINE_H

#include <camera/NdkCameraManager.h>
#include <camera/NdkCameraDevice.h>
#include <camera/NdkCameraCaptureSession.h>
#include <camera/NdkCaptureRequest.h>
#include <camera/NdkCameraMetadata.h>
#include <camera/NdkCameraMetadataTags.h>
#include <camera/NdkCameraError.h>
#include <media/NdkImageReader.h>
#include <media/NdkImage.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <android/log.h>

#include <string>
#include <vector>
#include <mutex>
#include <memory>
#include <chrono>

#define LOG_TAG "CameraNdkEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct NativeCameraMetadataInfo {
    std::string cameraId;
    int32_t facing = 0;              // 0: BACK, 1: FRONT, 2: EXTERNAL
    int32_t hardwareLevel = 0;       // 0: LIMITED, 1: FULL, 2: LEGACY, 3: LEVEL_3, 4: EXTERNAL
    int32_t sensorOrientation = 0;
    bool hasFlash = false;
    int32_t minIso = 100;
    int32_t maxIso = 3200;
    int64_t minExposureTimeNs = 100000LL;      // 0.1ms
    int64_t maxExposureTimeNs = 1000000000LL;  // 1s
    float minFocusDistance = 0.0f;             // diopters
    float maxDigitalZoom = 1.0f;
    bool supportsOis = false;
    std::vector<int32_t> supportedPreviewWidths;
    std::vector<int32_t> supportedPreviewHeights;
    std::vector<int32_t> supportedJpegWidths;
    std::vector<int32_t> supportedJpegHeights;
    std::vector<int32_t> supportedAfModes;
    std::vector<int32_t> supportedAeModes;
    std::vector<int32_t> supportedAwbModes;
};

struct NativeCameraStats {
    int64_t frameCount = 0;
    float fps = 0.0f;
    int64_t lastTimestampNs = 0;
    int64_t droppedFrames = 0;
    int64_t lastExposureDurationNs = 0;
    int32_t lastIso = 0;
};

struct NativeControlSettings {
    int32_t aeMode = ACAMERA_CONTROL_AE_MODE_ON;
    int32_t afMode = ACAMERA_CONTROL_AF_MODE_CONTINUOUS_PICTURE;
    int32_t awbMode = ACAMERA_CONTROL_AWB_MODE_AUTO;
    int32_t flashMode = ACAMERA_FLASH_MODE_OFF;
    int32_t iso = -1;                     // -1: Auto
    int64_t exposureTimeNs = -1;          // -1: Auto
    float focusDistance = -1.0f;          // -1: Auto
    int32_t exposureCompensation = 0;
    float zoomRatio = 1.0f;
    bool oisEnabled = false;
};

class CameraEngine {
public:
    CameraEngine();
    ~CameraEngine();

    bool Initialize();
    void Shutdown();

    std::vector<std::string> GetCameraIdList();
    bool GetCameraCharacteristics(const std::string& cameraId, NativeCameraMetadataInfo& outInfo);

    bool OpenCamera(const std::string& cameraId);
    bool SetPreviewSurface(ANativeWindow* window, int32_t width, int32_t height);
    bool CreateCaptureSession(int32_t previewWidth, int32_t previewHeight, int32_t captureWidth, int32_t captureHeight);
    bool StartStreaming();
    bool StopStreaming();
    bool UpdateControls(const NativeControlSettings& settings);
    bool TriggerAf(bool cancel);
    bool CaptureStill();
    std::vector<uint8_t> GetLatestCapturedImage();
    NativeCameraStats GetCaptureStats();
    void CloseCamera();

    // Callbacks from NDK
    void OnDeviceDisconnected(ACameraDevice* device);
    void OnDeviceError(ACameraDevice* device, int error);
    void OnSessionClosed(ACameraCaptureSession* session);
    void OnSessionReady(ACameraCaptureSession* session);
    void OnSessionActive(ACameraCaptureSession* session);
    void OnCaptureStarted(ACameraCaptureSession* session, const ACaptureRequest* request, int64_t timestamp);
    void OnCaptureCompleted(ACameraCaptureSession* session, ACaptureRequest* request, const ACameraMetadata* result);
    void OnCaptureFailed(ACameraCaptureSession* session, ACaptureRequest* request, ACameraCaptureFailure* failure);
    void OnImageAvailable(AImageReader* reader);

private:
    std::mutex mMutex;
    ACameraManager* mCameraManager = nullptr;
    ACameraDevice* mCameraDevice = nullptr;
    std::string mActiveCameraId;

    ACameraCaptureSession* mCaptureSession = nullptr;
    ACaptureRequest* mPreviewRequest = nullptr;
    ACaptureRequest* mStillRequest = nullptr;

    ACameraOutputTarget* mPreviewTarget = nullptr;
    ACameraOutputTarget* mImageReaderTarget = nullptr;

    ANativeWindow* mPreviewWindow = nullptr;
    AImageReader* mImageReader = nullptr;
    ANativeWindow* mImageReaderWindow = nullptr;

    ACaptureSessionOutputContainer* mOutputContainer = nullptr;
    ACaptureSessionOutput* mPreviewSessionOutput = nullptr;
    ACaptureSessionOutput* mImageReaderSessionOutput = nullptr;

    int32_t mActiveArrayWidth = 4000;
    int32_t mActiveArrayHeight = 3000;

    NativeControlSettings mCurrentSettings;
    NativeCameraStats mStats;
    std::chrono::steady_clock::time_point mLastFpsCalcTime;
    int64_t mFramesSinceLastCalc = 0;

    std::vector<uint8_t> mLatestJpegBytes;
    bool mHasNewCapturedImage = false;
    bool mIsStreaming = false;

    void ApplySettingsToRequest(ACaptureRequest* request, const NativeControlSettings& settings);
    void ResetStats();
};

#endif // CAMERA_ENGINE_H
