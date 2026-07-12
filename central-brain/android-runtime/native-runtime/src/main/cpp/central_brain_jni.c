#include "central_brain_native.h"

#include <jni.h>
#include <stdint.h>

#define CB_SNAPSHOT_FIELD_COUNT 10

static cb_runtime_t *cb_from_handle(jlong handle) {
    return (cb_runtime_t *) (uintptr_t) handle;
}

static jlong cb_to_handle(cb_runtime_t *runtime) {
    return (jlong) (uintptr_t) runtime;
}

static void cb_throw_illegal_state(JNIEnv *env, const char *message) {
    jclass exception_class = (*env)->FindClass(env, "java/lang/IllegalStateException");
    if (exception_class != NULL) {
        (void) (*env)->ThrowNew(env, exception_class, message);
        (*env)->DeleteLocalRef(env, exception_class);
    }
}

static jlong cb_jni_create(JNIEnv *env, jclass clazz, jint abi_version, jint max_slots) {
    cb_runtime_config_v1_t config = {
            .struct_size = (uint32_t) sizeof(cb_runtime_config_v1_t),
            .abi_version = (uint32_t) abi_version,
            .max_active_slots = (uint32_t) max_slots,
            .reserved_flags = 0U,
    };
    cb_runtime_t *runtime = NULL;
    cb_status_t status;

    (void) clazz;
    if (abi_version < 0 || max_slots < 0) {
        cb_throw_illegal_state(env, "negative native runtime configuration");
        return (jlong) 0;
    }
    status = cb_runtime_create_v1(&config, &runtime);
    if (status != CB_STATUS_OK) {
        cb_throw_illegal_state(env, cb_status_name(status));
        return (jlong) 0;
    }
    return cb_to_handle(runtime);
}

static jlongArray cb_jni_snapshot(JNIEnv *env, jclass clazz, jlong handle) {
    cb_runtime_health_v1_t health = {
            .struct_size = (uint32_t) sizeof(cb_runtime_health_v1_t),
            .abi_version = CB_NATIVE_ABI_VERSION,
    };
    jlong values[CB_SNAPSHOT_FIELD_COUNT];
    jlongArray result;
    cb_status_t status;

    (void) clazz;
    status = cb_runtime_get_health_v1(cb_from_handle(handle), &health);
    if (status != CB_STATUS_OK) {
        cb_throw_illegal_state(env, cb_status_name(status));
        return NULL;
    }

    values[0] = (jlong) health.abi_version;
    values[1] = (jlong) health.initialized;
    values[2] = (jlong) health.max_active_slots;
    values[3] = (jlong) health.active_slots;
    values[4] = (jlong) health.software_provider_available;
    values[5] = (jlong) health.vendor_npu_provider_available;
    values[6] = (jlong) health.hardware_accessed;
    values[7] = (jlong) health.generation;
    values[8] = (jlong) health.last_status;
    values[9] = (jlong) CB_NATIVE_MAX_SLOTS;

    result = (*env)->NewLongArray(env, CB_SNAPSHOT_FIELD_COUNT);
    if (result == NULL) {
        return NULL;
    }
    (*env)->SetLongArrayRegion(env, result, 0, CB_SNAPSHOT_FIELD_COUNT, values);
    return result;
}

static jlong cb_jni_acquire_slot(JNIEnv *env, jclass clazz, jlong handle) {
    uint64_t lease_id = UINT64_C(0);
    cb_status_t status;

    (void) env;
    (void) clazz;
    status = cb_runtime_acquire_slot_v1(cb_from_handle(handle), &lease_id);
    if (status != CB_STATUS_OK) {
        return -(jlong) status;
    }
    return (jlong) lease_id;
}

static jint cb_jni_release_slot(
        JNIEnv *env,
        jclass clazz,
        jlong handle,
        jlong lease_id) {
    (void) env;
    (void) clazz;
    if (lease_id <= 0) {
        return (jint) CB_STATUS_INVALID_ARGUMENT;
    }
    return (jint) cb_runtime_release_slot_v1(
            cb_from_handle(handle),
            (uint64_t) lease_id);
}

static jint cb_jni_destroy(JNIEnv *env, jclass clazz, jlong handle) {
    (void) env;
    (void) clazz;
    return (jint) cb_runtime_destroy_v1(cb_from_handle(handle));
}

/* JNINativeMethod requires a function pointer in a void * field. */
#if defined(__clang__)
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wpedantic"
#elif defined(__GNUC__)
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wpedantic"
#endif
static const JNINativeMethod CB_NATIVE_METHODS[] = {
        {"nativeCreate", "(II)J", (void *) cb_jni_create},
        {"nativeSnapshot", "(J)[J", (void *) cb_jni_snapshot},
        {"nativeAcquireSlot", "(J)J", (void *) cb_jni_acquire_slot},
        {"nativeReleaseSlot", "(JJ)I", (void *) cb_jni_release_slot},
        {"nativeDestroy", "(J)I", (void *) cb_jni_destroy},
};
#if defined(__clang__)
#pragma clang diagnostic pop
#elif defined(__GNUC__)
#pragma GCC diagnostic pop
#endif

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env = NULL;
    jclass runtime_class;

    (void) reserved;
    if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    runtime_class = (*env)->FindClass(env, "com/centralbrain/nativebridge/NativeRuntime");
    if (runtime_class == NULL) {
        return JNI_ERR;
    }
    if ((*env)->RegisterNatives(
                env,
                runtime_class,
                CB_NATIVE_METHODS,
                (jint) (sizeof(CB_NATIVE_METHODS) / sizeof(CB_NATIVE_METHODS[0]))) != JNI_OK) {
        (*env)->DeleteLocalRef(env, runtime_class);
        return JNI_ERR;
    }
    (*env)->DeleteLocalRef(env, runtime_class);
    return JNI_VERSION_1_6;
}
