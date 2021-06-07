package net.earthcomputer.clientcommands.script;

import org.apache.commons.lang3.StringUtils;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.ArrayList;
import java.util.List;

public class BeanWrapper implements ProxyObject {
    private final Object delegate;

    private BeanWrapper(Object delegate) {
        this.delegate = delegate;
    }

    public static Object wrap(Object javaBean) {
        if (javaBean == null) {
            return null;
        }
        if (javaBean instanceof BeanWrapper) {
            return javaBean;
        }
        if (javaBean instanceof Value) {
            Value value = (Value) javaBean;
            if (value.isHostObject() && value.asHostObject() instanceof BeanWrapper) {
                return javaBean;
            }
        }
        return new BeanWrapper(javaBean);
    }

    Value getDelegate() {
        return Context.getCurrent().asValue(this.delegate);
    }

    @Override
    public Object getMember(String key) {
        Value delegate = Context.getCurrent().asValue(this.delegate);
        Value getter = findGetter(delegate, key);
        if (getter != null && getter.canExecute()) {
            return getter.execute();
        } else {
            return delegate.getMember(key);
        }
    }

    @Override
    public Object getMemberKeys() {
        Value delegate = Context.getCurrent().asValue(this.delegate);
        List<String> memberKeys = new ArrayList<>();
        for (String memberKey : delegate.getMemberKeys()) {
            memberKeys.add(memberKey);
            if (memberKey.startsWith("get") && memberKey.length() > 3 && Character.isUpperCase(memberKey.charAt(3))) {
                memberKeys.add(StringUtils.uncapitalize(memberKey.substring(3)));
            } else if (memberKey.startsWith("is") && memberKey.length() > 2 && Character.isUpperCase(memberKey.charAt(2))) {
                memberKeys.add(StringUtils.uncapitalize(memberKey.substring(2)));
            }
        }
        return memberKeys;
    }

    @Override
    public boolean hasMember(String key) {
        Value delegate = Context.getCurrent().asValue(this.delegate);
        return delegate.hasMember(key) || findGetter(delegate, key) != null;
    }

    @Override
    public void putMember(String key, Value value) {
        Value delegate = Context.getCurrent().asValue(this.delegate);
        Value setter = findSetter(delegate, key);
        if (setter != null && setter.canExecute()) {
            setter.execute(value);
        } else {
            delegate.putMember(key, value);
        }
    }

    private Value findGetter(Value delegate, String propertyName) {
        if (Character.isUpperCase(propertyName.charAt(0))) {
            return null;
        }
        propertyName = StringUtils.capitalize(propertyName);
        Value getter = delegate.getMember("get" + propertyName);
        if (getter != null) {
            return getter;
        }
        return delegate.getMember("is" + propertyName);
    }

    private Value findSetter(Value delegate, String propertyName) {
        if (Character.isUpperCase(propertyName.charAt(0))) {
            return null;
        }
        return delegate.getMember("set" + StringUtils.capitalize(propertyName));
    }
}
