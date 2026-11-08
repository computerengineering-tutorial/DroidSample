#include <jni.h>
#include <string>
#include <vector>
#include "camera_engine.h"

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_droidsample_camera_CameraNdkBridge_nativeInit(JNIEnv* env, jobject thiz) {
    return JNI_TRUE;
}

JNIEXPORT jlong JNICALL
Java_com_example_droidsample_camera_CameraNdkBridge_nativeCreateEngine(JNIEnv* env, jobject thiz) {
    auto* engine = new CameraEngine();
    if (!engine->Initialize()) {
        delete engine;
        return 0L;
    }
    return reinterpret_cast<jlong>(engine);
}

JNIEXPORT void JNICALL
Java_com_example_droidsample_camera_CameraNdkBridge_nativeDestroyEngine(JNIEnv* env, jobject thiz, jlong handle) {
    auto* engine = reinterpret_cast<CameraEngine*>(handle);
    if (engine) {
        engine->Shutdown();
        delete engine;
    }
}

JNIEXPORT jobjectArray JNICALL
Java_com_example_droidsample_camera_CameraNdkBridge_nativeGetCameraIdList(JNIEnv* env, jobject thiz, jlong handle) {
    auto* engine = reinterpret_cast<CameraEngine*>(handle);
    if (!engine) {
        return env->NewObjectArray(0, env->FindClass("java/lang/String"), nullptr);
    }

    std::vector<std::string> ids = engine->GetCameraIdList();
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray array = env->NewObjectArray(ids.size(), stringClass, nullptr);

    for (size_t i = 0; i < ids.size(); ++i) {
        jstring str = env->NewStringUTF(ids[i].c_str());
        env->SetObjectArrayElement(array, i, str);
        env->DeleteLocalRef(str);
    }
    return array;
}

JNIEXPORT jboolean JNICALL
Java_com_example_droidsample_camera_CameraNdkBridge_nativePopulateCharacteristics(
        JNIEnv* env, jobject thiz, jlong handle, jstring jCameraId, jobject jHolder) {
    auto* engine = reinterpret_cast<CameraEngine*>(handle);
    if (!engine || !jCameraId || !jHolder) return JNI_FALSE;

    const char* cCameraId = env->GetStringUTFChars(jCameraId, nullptr);
    NativeCameraMetadataInfo info;
    bool success = engine->GetCameraCharacteristics(cCameraId, info);
    env->ReleaseStringUTFChars(jCameraId, cCameraId);

    if (!success) return JNI_FALSE;

    jclass holderClass = env->GetObjectClass(jHolder);

    // Set ints/booleans
    jfieldID fFacing = env->GetFieldID(holderClass, "facing", "I");
    env->SetIntField(jHolder, fFacing, info.facing);

    jfieldID fHw = env->GetFieldID(holderClass, "hardwareLevel", "I");
    env->SetIntField(jHolder, fHw, info.hardwareLevel);

    jfieldID fOrient = env->GetFieldID(holderClass, "sensorOrientation", "I");
    env->SetIntField(jHolder, fOrient, info.sensorOrientation);

    jfieldID fFlash = env->GetFieldID(holderClass, "hasFlash", "Z");
    env->SetBooleanField(jHolder, fFlash, info.hasFlash ? JNI_TRUE : JNI_FALSE);

    jfieldID fMinIso = env->GetFieldID(holderClass, "minIso", "I");
    env->SetIntField(jHolder, fMinIso, info.minIso);

    jfieldID fMaxIso = env->GetFieldID(holderClass, "maxIso", "I");
    env->SetIntField(jHolder, fMaxIso, info.maxIso);

    jfieldID fMinExp = env->GetFieldID(holderClass, "minExposureNs", "J");
    env->SetLongField(jHolder, fMinExp, info.minExposureTimeNs);

    jfieldID fMaxExp = env->GetFieldID(holderClass, "maxExposureNs", "J");
    env->SetLongField(jHolder, fMaxExp, info.maxExposureTimeNs);

    jfieldID fMinFd = env->GetFieldID(holderClass, "minFocusDistance", "F");
    env->SetFloatField(jHolder, fMinFd, info.minFocusDistance);

    jfieldID fMaxZoom = env->GetFieldID(holderClass, "maxDigitalZoom", "F");
    env->SetFloatField(jHolder, fMaxZoom, info.maxDigitalZoom);

    jfieldID fOis = env->GetFieldID(holderClass, "supportsOis", "Z");
    env->SetBooleanField(jHolder, fOis, info.supportsOis ? JNI_TRUE : JNI_FALSE);

    // Preview sizes flat array: [w1, h1, w2, h2, ...]
    std::vector<jint> previewRes;
    for (size_t i = 0; i < info.supportedPreviewWidths.size(); ++i) {
        previewRes.push_back(info.supportedPreviewWidths[i]);
        previewRes.push_back(info.supportedPreviewHeights[i]);
    }
    jintArray jPreviewArr = env->NewIntArray(previewRes.size());
    if (jPreviewArr) {
        env->SetIntArrayRegion(jPreviewArr, 0, previewRes.size(), previewRes.data());
        jfieldID fPreviewRes = env->GetFieldID(holderClass, "previewResolutions", "[I");
        env->SetObjectField(jHolder, fPreviewRes, jPreviewArr);
        env->DeleteLocalRef(jPreviewArr);
    }

    // JPEG sizes flat array
    std::vector<jint> jpegRes;
    for (size_t i = 0; i < info.supportedJpegWidths.size(); ++i) {
        jpegRes.push_back(info.supportedJpegWidths[i]);
        jpegRes.push_back(info.supportedJpegHeights[i]);
    }
    jintArray jJpegArr = env->NewIntArray(jpegRes.size());
    if (jJpegArr) {
        env->SetIntArrayRegion(jJpegArr, 0, jpegRes.size(), jpegRes.data());
        jfieldID fJpegRes = env->GetFieldID(holderClass, "jpegResolutions", "[I");
        env->SetObjectField(jHolder, fJpegRes, jJpegArr);
        env->DeleteLocalRef(jJpegArr);
    }

    // AF Modes
    jintArray jAfArr = env->NewIntArray(info.supportedAfModes.size());
    if (jAfArr) {
        env->SetIntArrayRegion(jAfArr, 0, info.supportedAfModes.size(), info.supportedAfModes.data());
        jfieldID fAf = env->GetFieldID(holderClass, "supportedAfModes", "[I");
        env->SetObjectField(jHolder, fAf, jAfArr);
        env->DeleteLocalRef(jAfArr);
    }

    // AE Modes
    jintArray jAeArr = env->NewIntArray(info.supportedAeModes.size());
    if (jAeArr) {
        env->SetIntArrayRegion(jAeArr, 0, info.supportedAeModes.size(), info.supportedAeModes.data());
        jfieldID fAe = env->GetFieldID(holderClass, "supportedAeModes", "[I");
        env->SetObjectField(jHolder, fAe, jAeArr);
        env->DeleteLocalRef(jAeArr);
    }

    // AWB Modes
    jintArray jAwbArr = env->NewIntArray(info.supportedAwbModes.size());
    if (jAwbArr) {
        env->SetIntArrayRegion(jAwbArr, 0, info.supportedAwbModes.size(), info.supportedAwbModes.data());
        jfieldID fAwb = env->GetFieldID(holderClass, "supportedAwbModes", "[I");
        env->SetObjectField(jHolder, fAwb, jAwbArr);
        env->DeleteLocalRef(jAwbArr);
    }

    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_droidsample_camera_CameraNdkBridge_nativeOpenCamera(
        JNIEnv* env, jobject thiz, jlong handle, jstring jCameraId) {
    auto* engine = reinterpret_cast<CameraEngine*>(handle);
    if (!engine || !jCameraId) return JNI_FALSE;

    const char* cCameraId = env->GetStringUTFChars(jCameraId, nullptr);
    bool result = engine->OpenCamera(cCameraId);
    env->ReleaseStringUTFChars(jCameraId, cCameraId);
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_droidsample_camera_CameraNdkBridge_nativeSetPreviewSurface(
        JNIEnv* env, jobject thiz, jlong handle, jobject jSurface, jint width, jint height) {
    auto* engine = reinterpret_cast<CameraEngine*>(handle);
    if (!engine) return JNI_FALSE;

    ANativeWindow* window = nullptr;
    if (jSurface) {
        window = ANativeWindow_fromSurface(env, jSurface);
        if (!window) {
            return JNI_FALSE;
        }
    }
    return engine->SetPreviewSurface(window, width, height) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_droidsample_camera_CameraNdkBridge_nativeCreateSession(
        JNIEnv* env, jobject thiz, jlong handle, jint previewW, jint previewH, jint capW, jint capH) {
    auto* engine = reinterpret_cast<CameraEngine*>(handle);
    if (!engine) return JNI_FALSE;
    return engine->CreateCaptureSession(previewW, previewH, capW, capH) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_droidsample_camera_CameraNdkBridge_nativeStartStreaming(
        JNIEnv* env, jobject thiz, jlong handle) {
    auto* engine = reinterpret_cast<CameraEngine*>(handle);
    if (!engine) return JNI_FALSE;
    return engine->StartStreaming() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_droidsample_camera_CameraNdkBridge_nativeStopStreaming(
        JNIEnv* env, jobject thiz, jlong handle) {
    auto* engine = reinterpret_cast<CameraEngine*>(handle);
    if (!engine) return JNI_FALSE;
    return engine->StopStreaming() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_droidsample_camera_CameraNdkBridge_nativeUpdateControls(
        JNIEnv* env, jobject thiz, jlong handle,
        jint aeMode, jint afMode, jint awbMode, jint flashMode,
        jint iso, jlong exposureTimeNs, jfloat focusDistance,
        jint exposureComp, jfloat zoomRatio, jboolean ois) {
    auto* engine = reinterpret_cast<CameraEngine*>(handle);
    if (!engine) return JNI_FALSE;

    NativeControlSettings settings;
    settings.aeMode = aeMode;
    settings.afMode = afMode;
    settings.awbMode = awbMode;
    settings.flashMode = flashMode;
    settings.iso = iso;
    settings.exposureTimeNs = exposureTimeNs;
    settings.focusDistance = focusDistance;
    settings.exposureCompensation = exposureComp;
    settings.zoomRatio = zoomRatio;
    settings.oisEnabled = (ois == JNI_TRUE);

    return engine->UpdateControls(settings) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_droidsample_camera_CameraNdkBridge_nativeTriggerAf(
        JNIEnv* env, jobject thiz, jlong handle, jboolean cancel) {
    auto* engine = reinterpret_cast<CameraEngine*>(handle);
    if (!engine) return JNI_FALSE;
    return engine->TriggerAf(cancel == JNI_TRUE) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_droidsample_camera_CameraNdkBridge_nativeCaptureStill(
        JNIEnv* env, jobject thiz, jlong handle) {
    auto* engine = reinterpret_cast<CameraEngine*>(handle);
    if (!engine) return JNI_FALSE;
    return engine->CaptureStill() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jbyteArray JNICALL
Java_com_example_droidsample_camera_CameraNdkBridge_nativeGetLatestCapturedImage(
        JNIEnv* env, jobject thiz, jlong handle) {
    auto* engine = reinterpret_cast<CameraEngine*>(handle);
    if (!engine) return nullptr;

    std::vector<uint8_t> bytes = engine->GetLatestCapturedImage();
    if (bytes.empty()) return nullptr;

    jbyteArray array = env->NewByteArray(bytes.size());
    if (array) {
        env->SetByteArrayRegion(array, 0, bytes.size(), reinterpret_cast<const jbyte*>(bytes.data()));
    }
    return array;
}

JNIEXPORT jdoubleArray JNICALL
Java_com_example_droidsample_camera_CameraNdkBridge_nativeGetCaptureStats(
        JNIEnv* env, jobject thiz, jlong handle) {
    auto* engine = reinterpret_cast<CameraEngine*>(handle);
    if (!engine) return nullptr;

    NativeCameraStats stats = engine->GetCaptureStats();
    jdouble statsArr[6] = {
        static_cast<jdouble>(stats.frameCount),
        static_cast<jdouble>(stats.fps),
        static_cast<jdouble>(stats.lastTimestampNs),
        static_cast<jdouble>(stats.droppedFrames),
        static_cast<jdouble>(stats.lastExposureDurationNs),
        static_cast<jdouble>(stats.lastIso)
    };

    jdoubleArray array = env->NewDoubleArray(6);
    if (array) {
        env->SetDoubleArrayRegion(array, 0, 6, statsArr);
    }
    return array;
}

JNIEXPORT void JNICALL
Java_com_example_droidsample_camera_CameraNdkBridge_nativeCloseCamera(
        JNIEnv* env, jobject thiz, jlong handle) {
    auto* engine = reinterpret_cast<CameraEngine*>(handle);
    if (engine) {
        engine->CloseCamera();
    }
}

} // extern "C"
