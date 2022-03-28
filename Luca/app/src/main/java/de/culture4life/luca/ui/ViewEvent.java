package de.culture4life.luca.ui;

import androidx.lifecycle.LiveData;

import java.util.Objects;

/**
 * Used as a wrapper for data that is exposed via a {@link LiveData} that represents an event.
 */
public class ViewEvent<T> {

    private T value;
    private boolean handled;

    public ViewEvent(T value) {
        this.value = value;
    }

    public T getValueAndMarkAsHandled() {
        handled = true;
        return value;
    }

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
    }

    public boolean hasBeenHandled() {
        return handled;
    }

    public void setHandled(boolean handled) {
        this.handled = handled;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ViewEvent<?> viewEvent = (ViewEvent<?>) o;
        return handled == viewEvent.handled && Objects.equals(value, viewEvent.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, handled);
    }

}
