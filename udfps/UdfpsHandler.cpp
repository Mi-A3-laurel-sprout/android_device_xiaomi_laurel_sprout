/*
 * Copyright (C) 2022 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#define LOG_TAG "UdfpsHander.laurel_sprout"

#include "UdfpsHandler.h"

#include <android-base/logging.h>
#include <fcntl.h>
#include <poll.h>
#include <thread>
#include <unistd.h>
#include <android-base/strings.h>
#include <cutils/properties.h>
#include <hardware/hardware.h>
#include <inttypes.h>
#include <fstream>

#define COMMAND_NIT 10
#define PARAM_NIT_FOD 3
#define PARAM_NIT_NONE 0

#define FOD_HBM_PATH "/sys/devices/platform/soc/soc:qcom,dsi-display/fod_hbm"
#define FOD_HBM_ON 1
#define FOD_HBM_OFF 0

#define FOD_DIM_PATH "/sys/devices/platform/soc/soc:qcom,dsi-display/dimlayer_hbm"
#define FOD_DIM_ON 1
#define FOD_DIM_OFF 0

#define FOD_STATUS_PATH "/sys/class/touch/tp_dev/fod_status"
#define FOD_STATUS_ON 1
#define FOD_STATUS_OFF 0

template <typename T>
static void set(const std::string& path, const T& value) {
    std::ofstream file(path);
    file << value;
}

class LaurelSproutUdfpsHander : public UdfpsHandler {
  public:
    void init(fingerprint_device_t *device) {
        mDevice = device;
    }

    void onFingerDown(uint32_t /*x*/, uint32_t /*y*/, float /*minor*/, float /*major*/) {
        set(FOD_STATUS_PATH, FOD_STATUS_ON);
        set(FOD_DIM_PATH, FOD_DIM_ON);
        std::thread([this]() {
            std::this_thread::sleep_for(std::chrono::milliseconds(90));
            set(FOD_HBM_PATH, FOD_HBM_ON);
            std::this_thread::sleep_for(std::chrono::milliseconds(40));
            mDevice->extCmd(mDevice, COMMAND_NIT, PARAM_NIT_FOD);
        }).detach();
    }

    void onFingerUp() {
        set(FOD_STATUS_PATH, FOD_STATUS_OFF);
        set(FOD_HBM_PATH, FOD_HBM_OFF);
        std::thread([this]() {
            std::this_thread::sleep_for(std::chrono::microseconds(30));
            mDevice->extCmd(mDevice, COMMAND_NIT, PARAM_NIT_NONE);
            set(FOD_DIM_PATH, FOD_DIM_OFF);
        }).detach();
    }
    
    void onAcquired(int32_t /*result*/, int32_t /*vendorCode*/) {
        // nothing
    }

    void cancel() {
        // nothing
    }
    
    void onHideUdfpsOverlay() {
        // nothing
    }

    void onShowUdfpsOverlay() {
        // nothing
    }
  private:
    fingerprint_device_t *mDevice;
};

static UdfpsHandler* create() {
    return new LaurelSproutUdfpsHander();
}

static void destroy(UdfpsHandler* handler) {
    delete handler;
}

extern "C" UdfpsHandlerFactory UDFPS_HANDLER_FACTORY = {
    .create = create,
    .destroy = destroy,
};
