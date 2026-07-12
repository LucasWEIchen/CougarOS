#include "central_brain_native.h"

#include <pthread.h>
#include <stdlib.h>
#include <string.h>

struct cb_runtime {
    pthread_mutex_t mutex;
    uint32_t max_active_slots;
    uint32_t active_slots;
    uint64_t generation;
    uint64_t next_lease_id;
    uint64_t leases[CB_NATIVE_MAX_SLOTS];
    cb_status_t last_status;
};

static cb_status_t cb_validate_runtime(cb_runtime_t *runtime) {
    return runtime == NULL ? CB_STATUS_INVALID_ARGUMENT : CB_STATUS_OK;
}

static void cb_set_last_status(cb_runtime_t *runtime, cb_status_t status) {
    if (runtime != NULL) {
        runtime->last_status = status;
    }
}

static int cb_lease_exists_locked(const cb_runtime_t *runtime, uint64_t lease_id) {
    uint32_t index;

    for (index = 0U; index < runtime->max_active_slots; ++index) {
        if (runtime->leases[index] == lease_id) {
            return 1;
        }
    }
    return 0;
}

static uint64_t cb_next_lease_locked(cb_runtime_t *runtime) {
    uint32_t attempt;
    uint64_t candidate;

    for (attempt = 0U; attempt <= runtime->max_active_slots; ++attempt) {
        candidate = runtime->next_lease_id++;
        if (runtime->next_lease_id == UINT64_C(0)) {
            runtime->next_lease_id = UINT64_C(1);
        }
        if (candidate != UINT64_C(0) && !cb_lease_exists_locked(runtime, candidate)) {
            return candidate;
        }
    }
    return UINT64_C(0);
}

cb_status_t cb_runtime_create_v1(
        const cb_runtime_config_v1_t *config,
        cb_runtime_t **out_runtime) {
    cb_runtime_t *runtime;

    if (config == NULL || out_runtime == NULL) {
        return CB_STATUS_INVALID_ARGUMENT;
    }
    *out_runtime = NULL;
    if (config->struct_size < sizeof(cb_runtime_config_v1_t)
            || config->abi_version != CB_NATIVE_ABI_VERSION) {
        return CB_STATUS_ABI_MISMATCH;
    }
    if (config->max_active_slots == 0U
            || config->max_active_slots > CB_NATIVE_MAX_SLOTS
            || config->reserved_flags != 0U) {
        return CB_STATUS_INVALID_ARGUMENT;
    }

    runtime = calloc(1U, sizeof(*runtime));
    if (runtime == NULL) {
        return CB_STATUS_OUT_OF_MEMORY;
    }
    if (pthread_mutex_init(&runtime->mutex, NULL) != 0) {
        free(runtime);
        return CB_STATUS_INTERNAL_ERROR;
    }
    runtime->max_active_slots = config->max_active_slots;
    runtime->generation = UINT64_C(1);
    runtime->next_lease_id = UINT64_C(1);
    runtime->last_status = CB_STATUS_OK;
    *out_runtime = runtime;
    return CB_STATUS_OK;
}

cb_status_t cb_runtime_get_health_v1(
        cb_runtime_t *runtime,
        cb_runtime_health_v1_t *out_health) {
    cb_status_t status = cb_validate_runtime(runtime);

    if (status != CB_STATUS_OK || out_health == NULL) {
        return CB_STATUS_INVALID_ARGUMENT;
    }
    if (out_health->struct_size < sizeof(cb_runtime_health_v1_t)
            || out_health->abi_version != CB_NATIVE_ABI_VERSION) {
        return CB_STATUS_ABI_MISMATCH;
    }

    if (pthread_mutex_lock(&runtime->mutex) != 0) {
        return CB_STATUS_INTERNAL_ERROR;
    }
    out_health->initialized = 1U;
    out_health->max_active_slots = runtime->max_active_slots;
    out_health->active_slots = runtime->active_slots;
    out_health->software_provider_available = 0U;
    out_health->vendor_npu_provider_available = 0U;
    out_health->hardware_accessed = 0U;
    out_health->generation = runtime->generation;
    out_health->last_status = (int32_t) runtime->last_status;
    out_health->reserved = 0U;
    runtime->last_status = CB_STATUS_OK;
    (void) pthread_mutex_unlock(&runtime->mutex);
    return CB_STATUS_OK;
}

cb_status_t cb_runtime_acquire_slot_v1(
        cb_runtime_t *runtime,
        uint64_t *out_lease_id) {
    uint32_t index;
    uint64_t lease_id;

    if (runtime == NULL || out_lease_id == NULL) {
        return CB_STATUS_INVALID_ARGUMENT;
    }
    *out_lease_id = UINT64_C(0);
    if (pthread_mutex_lock(&runtime->mutex) != 0) {
        return CB_STATUS_INTERNAL_ERROR;
    }
    if (runtime->active_slots >= runtime->max_active_slots) {
        cb_set_last_status(runtime, CB_STATUS_CAPACITY_EXHAUSTED);
        (void) pthread_mutex_unlock(&runtime->mutex);
        return CB_STATUS_CAPACITY_EXHAUSTED;
    }

    index = 0U;
    while (index < runtime->max_active_slots && runtime->leases[index] != UINT64_C(0)) {
        ++index;
    }
    if (index >= runtime->max_active_slots) {
        cb_set_last_status(runtime, CB_STATUS_INTERNAL_ERROR);
        (void) pthread_mutex_unlock(&runtime->mutex);
        return CB_STATUS_INTERNAL_ERROR;
    }

    lease_id = cb_next_lease_locked(runtime);
    if (lease_id == UINT64_C(0)) {
        cb_set_last_status(runtime, CB_STATUS_INTERNAL_ERROR);
        (void) pthread_mutex_unlock(&runtime->mutex);
        return CB_STATUS_INTERNAL_ERROR;
    }
    runtime->leases[index] = lease_id;
    ++runtime->active_slots;
    ++runtime->generation;
    cb_set_last_status(runtime, CB_STATUS_OK);
    *out_lease_id = lease_id;
    (void) pthread_mutex_unlock(&runtime->mutex);
    return CB_STATUS_OK;
}

cb_status_t cb_runtime_release_slot_v1(
        cb_runtime_t *runtime,
        uint64_t lease_id) {
    uint32_t index;

    if (runtime == NULL || lease_id == UINT64_C(0)) {
        return CB_STATUS_INVALID_ARGUMENT;
    }
    if (pthread_mutex_lock(&runtime->mutex) != 0) {
        return CB_STATUS_INTERNAL_ERROR;
    }

    for (index = 0U; index < runtime->max_active_slots; ++index) {
        if (runtime->leases[index] == lease_id) {
            runtime->leases[index] = UINT64_C(0);
            --runtime->active_slots;
            ++runtime->generation;
            cb_set_last_status(runtime, CB_STATUS_OK);
            (void) pthread_mutex_unlock(&runtime->mutex);
            return CB_STATUS_OK;
        }
    }

    cb_set_last_status(runtime, CB_STATUS_NOT_FOUND);
    (void) pthread_mutex_unlock(&runtime->mutex);
    return CB_STATUS_NOT_FOUND;
}

cb_status_t cb_runtime_destroy_v1(cb_runtime_t *runtime) {
    if (runtime == NULL) {
        return CB_STATUS_INVALID_ARGUMENT;
    }
    if (pthread_mutex_lock(&runtime->mutex) != 0) {
        return CB_STATUS_INTERNAL_ERROR;
    }
    if (runtime->active_slots != 0U) {
        cb_set_last_status(runtime, CB_STATUS_BUSY);
        (void) pthread_mutex_unlock(&runtime->mutex);
        return CB_STATUS_BUSY;
    }
    (void) pthread_mutex_unlock(&runtime->mutex);
    (void) pthread_mutex_destroy(&runtime->mutex);
    memset(runtime, 0, sizeof(*runtime));
    free(runtime);
    return CB_STATUS_OK;
}

const char *cb_status_name(cb_status_t status) {
    switch (status) {
        case CB_STATUS_OK:
            return "OK";
        case CB_STATUS_INVALID_ARGUMENT:
            return "INVALID_ARGUMENT";
        case CB_STATUS_ABI_MISMATCH:
            return "ABI_MISMATCH";
        case CB_STATUS_OUT_OF_MEMORY:
            return "OUT_OF_MEMORY";
        case CB_STATUS_CAPACITY_EXHAUSTED:
            return "CAPACITY_EXHAUSTED";
        case CB_STATUS_NOT_FOUND:
            return "NOT_FOUND";
        case CB_STATUS_BUSY:
            return "BUSY";
        case CB_STATUS_CLOSED:
            return "CLOSED";
        case CB_STATUS_INTERNAL_ERROR:
            return "INTERNAL_ERROR";
        default:
            return "UNKNOWN";
    }
}
