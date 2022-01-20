package club.decencies.remapper.lunar.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class Chain<T> {

    protected int length;
    protected int pointer;
    protected final List<T> list;

    public Chain(List<T> list) {
        this.list = list;
    }

    public Chain(List<T> list, int startingIndex) {
        this.list = list;
        this.pointer = startingIndex;
    }

    public int length() {
        return length;
    }

    public Chain<T> length(Consumer<Integer> consumer) {
        consumer.accept(length);
        return this;
    }

    /**
     * Accept the next element in the supplied consumer, and push the pointer forward by 1.
     * @param consumer the consumer that accepts the next element.
     * @return the chain with the applied transformation changes.
     */
    public Chain<T> next(Consumer<T> consumer) {
        if (list.size() > pointer + 1) {
            consumer.accept(list.get(++pointer));
        }
        return this;
    }

    /**
     * Accept the previous element in the supplied consumer, and push the pointer back by 1.
     * @param consumer the consumer that accepts the previous element.
     * @return the chain with the applied transformation changes.
     */
    public Chain<T> previous(Consumer<T> consumer) {
        if (pointer - 1 > 0) {
            consumer.accept(list.get(--pointer));
        }
        return this;
    }

    /**
     * Adds a value onto the end of this chain.
     * @param value the value to add.
     * @return the chain with the applied transformation changes.
     */
    public Chain<T> add(T value) {
        list.add(value);
        return this;
    }

    /**
     * Remove the current selected element by the pointer.
     * @return the chain with the applied transformation changes.
     */
    public Chain<T> remove() {
        if (pointer - 1 > 0) {
            list.remove(pointer--);
        }
        return this;
    }

    /**
     * Moves the pointer by N elements if applicable.
     * @param elements the amount of elements to move the pointer by.
     * @return the chain with the applied transformation changes.
     */
    public Chain<T> pointer(int elements) {
        if (elements < 0) {
            if (pointer - elements > 0) {
                pointer -= elements;
            }
        } else {
            if (pointer + elements < list.size() - 1) {
                pointer += elements;
            }
        }
        return this;
    }

    /**
     * Move the current element at the {@link this#pointer} by {@code elements}
     * @param elements the amount of elements to move the element by.
     * @return the chain with the applied transformation changes.
     */
    public Chain<T> move(int elements) {
        T value = list.get(pointer);
        int valueIndex = pointer;
        list.remove(valueIndex);
        if (elements < 0) {
            if (pointer - elements > 0) {
                valueIndex -= elements;
            }
        } else {
            if (pointer + elements < list.size() - 1) {
                valueIndex += elements;
            }
        }
        list.set(valueIndex, value);
        return this;
    }

    /**
     * Insert an element at the {@code this#pointer}.
     * @param value the element to add.
     * @return the chain with the applied transformation changes.
     */
    public Chain<T> insert(T value) {
        list.add(pointer, value);
        return this;
    }

    /**
     * Replace the element at the {@code this#pointer} with the supplied element.
     * @param value the element.
     * @return the chain with the applied transformation changes.
     */
    public Chain<T> replace(T value) {
        list.set(pointer, value);
        return this;
    }

    public <R> Chain<R> map(Function<T, R> mapper) {
        Chain<R> chain = new Chain<>(new ArrayList<>());
        list.forEach(v -> chain.add(mapper.apply(v)));
        return chain;
    }

}
