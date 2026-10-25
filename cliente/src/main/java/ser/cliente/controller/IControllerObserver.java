package ser.cliente.controller;

import ser.cliente.model.Response;

public interface IControllerObserver {
    void onResponse(Response response);
}