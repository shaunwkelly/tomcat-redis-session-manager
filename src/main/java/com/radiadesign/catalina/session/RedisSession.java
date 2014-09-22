package com.radiadesign.catalina.session;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.Principal;

import org.apache.catalina.Manager;
import org.apache.catalina.session.StandardSession;


public class RedisSession extends StandardSession {
    protected boolean dirty;

    public RedisSession() {
        super(null);
        resetDirtyTracking();
    }

    public RedisSession(Manager manager) {
        super(manager);
        resetDirtyTracking();
    }


    public Boolean isDirty() {
        return dirty;
    }

    public void resetDirtyTracking() {
        dirty = false;
    }

    @Override
    public void setAttribute(String key, Object value) {
        dirty = true;
        super.setAttribute(key, value);
    }

    @Override
    public void removeAttribute(String name) {
        dirty = true;
        super.removeAttribute(name);
    }

    @Override
    public void setId(String id) {
        // Specifically do not call super(): it's implementation does unexpected things
        // like calling manager.remove(session.id) and manager.add(session).

        this.id = id;
    }

    @Override
    public void setPrincipal(Principal principal) {
        dirty = true;
        super.setPrincipal(principal);
    }

    @Override
    protected void readObject(ObjectInputStream stream)
            throws ClassNotFoundException, IOException {
        super.readObject(stream);
        dirty = stream.readBoolean();
    }

    @Override
    protected void writeObject(ObjectOutputStream stream) throws IOException {
        super.writeObject(stream);
        stream.writeBoolean(dirty);
    }
}
