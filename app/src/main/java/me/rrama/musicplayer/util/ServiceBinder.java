package me.rrama.musicplayer.util;

import android.app.Service;
import android.os.Binder;

/**
 * A {@link Binder} that you can retrieve the bound {@link Service} from.
 * @param <S> The {@link Service} class that you are binding to.
 */
public class ServiceBinder<S extends Service> extends Binder {

    private final S service;

    public ServiceBinder(S service) {
        this.service = service;
    }

    public S getService() {
        return service;
    }

}
