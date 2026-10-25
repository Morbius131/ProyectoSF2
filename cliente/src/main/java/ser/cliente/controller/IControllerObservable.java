package ser.cliente.controller;

import java.util.function.Consumer;
import ser.cliente.model.Response;

public interface IControllerObservable {
    void addObserver(Consumer<Response> observer);

    void removeObserver(Consumer<Response> observer);
}