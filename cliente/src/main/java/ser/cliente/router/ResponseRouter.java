package ser.cliente.router;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import ser.cliente.controller.IControllerObserver;
import ser.cliente.model.MessageType;
import ser.cliente.model.Response;

public final class ResponseRouter {
    private static final ResponseRouter INSTANCE = new ResponseRouter();

    private final Map<MessageType, List<IControllerObserver>> routes = new ConcurrentHashMap<>();

    private ResponseRouter() {
    }

    public static ResponseRouter getInstance() {
        return INSTANCE;
    }

    public void register(MessageType messageType, IControllerObserver observer) {
        routes.computeIfAbsent(messageType, ignored -> new CopyOnWriteArrayList<>()).add(observer);
    }

    public void unregister(MessageType messageType, IControllerObserver observer) {
        List<IControllerObserver> observers = routes.get(messageType);
        if (observers != null) {
            observers.remove(observer);
        }
    }

    public void dispatch(Response response) {
        List<IControllerObserver> observers = routes.get(response.type());
        if (observers == null) {
            return;
        }
        for (IControllerObserver observer : observers) {
            observer.onResponse(response);
        }
    }
}