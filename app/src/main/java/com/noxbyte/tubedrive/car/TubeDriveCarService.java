package com.noxbyte.tubedrive.car;

import androidx.annotation.NonNull;
import androidx.car.app.CarAppService;
import androidx.car.app.Session;
import androidx.car.app.validation.HostValidator;

public class TubeDriveCarService extends CarAppService {
    @NonNull
    @Override
    public HostValidator createHostValidator() {
        // Demo-grade: accept any host. A store build would verify the caller.
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR;
    }

    @NonNull
    @Override
    public Session onCreateSession() {
        return new TubeDriveSession();
    }
}
