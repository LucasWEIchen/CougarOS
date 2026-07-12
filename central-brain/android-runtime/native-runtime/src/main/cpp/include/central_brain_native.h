#ifndef CENTRAL_BRAIN_NATIVE_H
#define CENTRAL_BRAIN_NATIVE_H

#include <stddef.h>
#include <stdint.h>

#if defined(__GNUC__)
#define CB_API __attribute__((visibility("default")))
#else
#define CB_API
#endif

#ifdef __cplusplus
extern "C" {
#endif

#define CB_NATIVE_ABI_VERSION UINT32_C(1)
#define CB_NATIVE_MAX_SLOTS UINT32_C(64)

typedef struct cb_runtime cb_runtime_t;

typedef enum cb_status {
    CB_STATUS_OK = 0,
    CB_STATUS_INVALID_ARGUMENT = 1,
    CB_STATUS_ABI_MISMATCH = 2,
    CB_STATUS_OUT_OF_MEMORY = 3,
    CB_STATUS_CAPACITY_EXHAUSTED = 4,
    CB_STATUS_NOT_FOUND = 5,
    CB_STATUS_BUSY = 6,
    CB_STATUS_CLOSED = 7,
    CB_STATUS_INTERNAL_ERROR = 8
} cb_status_t;

typedef struct cb_runtime_config_v1 {
    uint32_t struct_size;
    uint32_t abi_version;
    uint32_t max_active_slots;
    uint32_t reserved_flags;
} cb_runtime_config_v1_t;

typedef struct cb_runtime_health_v1 {
    uint32_t struct_size;
    uint32_t abi_version;
    uint32_t initialized;
    uint32_t max_active_slots;
    uint32_t active_slots;
    uint32_t software_provider_available;
    uint32_t vendor_npu_provider_available;
    uint32_t hardware_accessed;
    uint64_t generation;
    int32_t last_status;
    uint32_t reserved;
} cb_runtime_health_v1_t;

CB_API cb_status_t cb_runtime_create_v1(
        const cb_runtime_config_v1_t *config,
        cb_runtime_t **out_runtime);

CB_API cb_status_t cb_runtime_get_health_v1(
        cb_runtime_t *runtime,
        cb_runtime_health_v1_t *out_health);

CB_API cb_status_t cb_runtime_acquire_slot_v1(
        cb_runtime_t *runtime,
        uint64_t *out_lease_id);

CB_API cb_status_t cb_runtime_release_slot_v1(
        cb_runtime_t *runtime,
        uint64_t lease_id);

/* The caller must serialize destroy against every other operation on this handle. */
CB_API cb_status_t cb_runtime_destroy_v1(cb_runtime_t *runtime);

CB_API const char *cb_status_name(cb_status_t status);

#ifdef __cplusplus
}
#endif

#endif
