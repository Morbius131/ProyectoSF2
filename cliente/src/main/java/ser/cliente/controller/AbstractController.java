package ser.cliente.controller;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import ser.cliente.model.MessageType;
import ser.cliente.model.Response;
import ser.cliente.router.ResponseRouter;

public abstract class AbstractController implements IControllerObservable, IControllerObserver {
    private final Set<MessageType> subscribedTypes;
    private final List<Consumer<Response>> observers;

    protected AbstractController(MessageType... subscribed) {
        this.subscribedTypes = EnumSet.noneOf(MessageType.class);
        this.observers = new CopyOnWriteArrayList<>();
        this.subscribedTypes.addAll(Arrays.asList(subscribed));
        ResponseRouter router = ResponseRouter.getInstance();
        for (MessageType type : subscribedTypes) {
            router.register(type, this);
        }
    }

    @Override
    public void addObserver(Consumer<Response> observer) {
        observers.add(observer);
    }

    @Override
    public void removeObserver(Consumer<Response> observer) {
        observers.remove(observer);
    }

    @Override
    public void onResponse(Response response) {
        if (!subscribedTypes.contains(response.type())) {
            return;
        }
        for (Consumer<Response> observer : observers) {
            observer.accept(response);
        }
    }

    protected void publish(Response response) {
        ResponseRouter.getInstance().dispatch(response);
    }
}