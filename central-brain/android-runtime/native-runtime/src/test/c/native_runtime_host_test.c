#include "central_brain_native.h"

#include <assert.h>
#include <pthread.h>
#include <stdint.h>
#include <stdio.h>

#define CONCURRENCY_THREADS 4
#define CONCURRENCY_ITERATIONS 1000

typedef struct worker_args {
    cb_runtime_t *runtime;
} worker_args_t;

static void *run_slot_worker(void *opaque) {
    worker_args_t *args = opaque;
    uint32_t iteration;

    for (iteration = 0U; iteration < CONCURRENCY_ITERATIONS; ++iteration) {
        uint64_t lease_id = UINT64_C(0);
        assert(cb_runtime_acquire_slot_v1(args->runtime, &lease_id) == CB_STATUS_OK);
        assert(lease_id != UINT64_C(0));
        assert(cb_runtime_release_slot_v1(args->runtime, lease_id) == CB_STATUS_OK);
    }
    return NULL;
}

static cb_runtime_health_v1_t health_of(cb_runtime_t *runtime) {
    cb_runtime_health_v1_t health = {
            .struct_size = (uint32_t) sizeof(cb_runtime_health_v1_t),
            .abi_version = CB_NATIVE_ABI_VERSION,
    };
    assert(cb_runtime_get_health_v1(runtime, &health) == CB_STATUS_OK);
    return health;
}

int main(void) {
    cb_runtime_config_v1_t config = {
            .struct_size = (uint32_t) sizeof(cb_runtime_config_v1_t),
            .abi_version = CB_NATIVE_ABI_VERSION,
            .max_active_slots = 2U,
            .reserved_flags = 0U,
    };
    cb_runtime_config_v1_t invalid_config = config;
    cb_runtime_t *runtime = NULL;
    cb_runtime_health_v1_t health;
    uint64_t first = UINT64_C(0);
    uint64_t second = UINT64_C(0);
    uint64_t rejected = UINT64_C(0);
    pthread_t threads[CONCURRENCY_THREADS];
    worker_args_t worker_args;
    uint32_t index;

    invalid_config.abi_version = CB_NATIVE_ABI_VERSION + 1U;
    assert(cb_runtime_create_v1(&invalid_config, &runtime) == CB_STATUS_ABI_MISMATCH);
    assert(runtime == NULL);

    assert(cb_runtime_create_v1(&config, &runtime) == CB_STATUS_OK);
    assert(runtime != NULL);
    health = health_of(runtime);
    assert(health.initialized == 1U);
    assert(health.max_active_slots == 2U);
    assert(health.active_slots == 0U);
    assert(health.software_provider_available == 0U);
    assert(health.vendor_npu_provider_available == 0U);
    assert(health.hardware_accessed == 0U);

    assert(cb_runtime_acquire_slot_v1(runtime, &first) == CB_STATUS_OK);
    assert(first != UINT64_C(0));
    assert(cb_runtime_acquire_slot_v1(runtime, &second) == CB_STATUS_OK);
    assert(second != UINT64_C(0));
    assert(second != first);
    assert(cb_runtime_acquire_slot_v1(runtime, &rejected) == CB_STATUS_CAPACITY_EXHAUSTED);
    assert(rejected == UINT64_C(0));

    health = health_of(runtime);
    assert(health.active_slots == 2U);
    assert(health.hardware_accessed == 0U);
    assert(cb_runtime_destroy_v1(runtime) == CB_STATUS_BUSY);
    assert(cb_runtime_release_slot_v1(runtime, UINT64_C(999999)) == CB_STATUS_NOT_FOUND);
    assert(cb_runtime_release_slot_v1(runtime, first) == CB_STATUS_OK);
    assert(cb_runtime_release_slot_v1(runtime, first) == CB_STATUS_NOT_FOUND);
    assert(cb_runtime_release_slot_v1(runtime, second) == CB_STATUS_OK);

    health = health_of(runtime);
    assert(health.active_slots == 0U);
    assert(health.generation >= UINT64_C(5));
    assert(cb_runtime_destroy_v1(runtime) == CB_STATUS_OK);

    config.max_active_slots = CONCURRENCY_THREADS;
    assert(cb_runtime_create_v1(&config, &runtime) == CB_STATUS_OK);
    worker_args.runtime = runtime;
    for (index = 0U; index < CONCURRENCY_THREADS; ++index) {
        assert(pthread_create(&threads[index], NULL, run_slot_worker, &worker_args) == 0);
    }
    for (index = 0U; index < CONCURRENCY_THREADS; ++index) {
        assert(pthread_join(threads[index], NULL) == 0);
    }
    health = health_of(runtime);
    assert(health.active_slots == 0U);
    assert(health.generation
            == UINT64_C(1) + (UINT64_C(2) * CONCURRENCY_THREADS * CONCURRENCY_ITERATIONS));
    assert(health.hardware_accessed == 0U);
    assert(cb_runtime_destroy_v1(runtime) == CB_STATUS_OK);

    puts("native_runtime_host_test_passed=true");
    puts("native_runtime_concurrency_verified=true");
    puts("native_vendor_npu_provider_available=false");
    puts("native_hardware_accessed=false");
    return 0;
}
