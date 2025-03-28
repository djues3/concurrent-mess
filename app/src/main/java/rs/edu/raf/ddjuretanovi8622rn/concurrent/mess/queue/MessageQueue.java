package rs.edu.raf.ddjuretanovi8622rn.concurrent.mess.queue;

import module java.base;

import rs.edu.raf.ddjuretanovi8622rn.concurrent.mess.command.Message;
import rs.edu.raf.ddjuretanovi8622rn.concurrent.mess.command.PoisonPill;

public final class MessageQueue {

    private final BlockingQueue<Message> messages;

    ///  Provides equivalent semantics to synchronized blocks
    private final Lock lock = new ReentrantLock();

    public MessageQueue() {
        messages = new PriorityBlockingQueue<>();
    }

    public boolean emit(Message m) {
        return messages.offer(m);
    }

    public Message take() throws InterruptedException {
        lock.lock();
        try {
            Message head = messages.peek();
            if (head instanceof PoisonPill) {
                return head;
            }
            Message m = messages.take();
            // Required since, technically, the producer can emit a PoisonPill in between
            // the peek and take calls.
            // It's probably physically impossible due to the extremely precise timing needed, but still.
            if (m instanceof PoisonPill) {
                this.emit(m);
                return m;
            }
            return m;
        } finally {
            lock.unlock();
        }
    }
}
