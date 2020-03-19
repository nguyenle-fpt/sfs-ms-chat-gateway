package com.symphony.sfs.ms.chat.datafeed;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class MultiListener<T> implements Consumer<T> {
  private Set<Consumer<T>> listeners = new HashSet<>();

  public void accept(T data) {
    listeners.forEach(l -> l.accept(data));
  }

  public void register(Consumer<T> listener) {
    listeners.add(listener);
  }

  public void unregister(Consumer<T> listener) {
    listeners.remove(listener);
  }
}
