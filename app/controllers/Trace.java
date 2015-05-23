package controllers;

import java.util.Arrays;
import java.util.List;

import play.Logger;
import play.libs.Json;

/**
 * @author Angel Nunez
 */
public class Trace<T> {

    private final Class clazz;
    private final String method;
    private final Object[] params;
    private final String requester;

    public Trace(Class clazz, String method, Object... params) {
        this.clazz = clazz;
        this.method = method;
        this.params = params;
        this.requester = "<requester>";
    }

    public T trace(Body<T> b) {
        called();

        long ti = System.currentTimeMillis();
        long tf;
        try {
            final T returnValue = b.execute(this);
            tf = System.currentTimeMillis();

            Object ret = returnValue;
            if (returnValue instanceof List) {
                ret = ((List) returnValue).size();
            }

            returned(tf - ti, ret);
            return returnValue;

        } catch (Exception e) {
            tf = System.currentTimeMillis();
            exceptioned(tf - ti, e);
            throw new RuntimeException(e);
        }
    }

    public void info(String s) {
        infoed(s);
    }

    public void error(String s, Throwable t) {
        errored(s, t);
    }

    public void error(String s) {
        errored(s, null);
    }

    public interface Body<T> {
        T execute(Trace trace) throws Exception;
    }

    private void called() {
        final MethodCall ret = new MethodCall(requester, clazz, method, params);
        safeLog(ret);
    }

    private void returned(Long time, Object retValue) {
        final MethodReturn ret = new MethodReturn(requester, clazz, method, params);
        ret.time = time;
        ret.returns = retValue;

        try {
            safeLog(ret);
        } catch (Exception e) {
            ret.params = params;
            ret.returns = "possible return not serializable";
            Logger.info("{}", Json.toJson(ret));
        }
    }

    private void exceptioned(Long time, Exception e) {
        final MethodException ret = new MethodException(requester, clazz, method, params);
        ret.time = time;
        ret.exception = e.getMessage(); //TODO:

        safeLog(ret);
    }

    private void infoed(String s) {
        final MethodInfo ret = new MethodInfo(requester, clazz, method, params);
        ret.info = s;

        safeLog(ret);
    }

    private void errored(String s, Throwable t) {
        final MethodError ret = new MethodError(requester, clazz, method, params);
        ret.info = s;
        ret.throwable = t == null ? null : t.getMessage(); //TODO

        safeLog(ret);
    }

    private void safeLog(MethodCall ret) {
        try {
            Logger.info("{}", Json.toJson(ret));
        } catch (Exception e) {
            ret.params = new String[]{"possible param not serializable"};
            Logger.info("{}", Json.toJson(ret));
        }
    }

    //

    private static class MethodCall {
        public String type = "call";
        public String requester;
        public String clazz;
        public final String method;
        public Object[] params;
        public long threadId;

        public MethodCall(String requester, Class clazz, String method, Object... params) {
            this.requester = requester;
            this.clazz = clazz.getSimpleName();
            this.threadId = Thread.currentThread().getId();
            this.method = method;
            this.params = params;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MethodCall)) return false;

            MethodCall that = (MethodCall) o;

            if (threadId != that.threadId) return false;
            if (!clazz.equals(that.clazz)) return false;
            if (!method.equals(that.method)) return false;
            // Probably incorrect - comparing Object[] arrays with Arrays.equals
            return Arrays.equals(params, that.params);
        }

        @Override
        public int hashCode() {
            int result = clazz.hashCode();
            result = 31 * result + method.hashCode();
            result = 31 * result + Arrays.hashCode(params);
            result = 31 * result + (int) (threadId ^ (threadId >>> 32));
            return result;
        }
    }

    private static class MethodReturn extends MethodCall {
        public String type = "rtrn";
        public Object returns;
        public Long time;

        public MethodReturn(String requester, Class clazz, String method, Object... params) {
            super(requester, clazz, method, params);
        }
    }

    private static class MethodException extends MethodCall {
        public String type = "xptn";
        public Long time;
        public String exception;

        public MethodException(String requester, Class clazz, String method, Object... params) {
            super(requester, clazz, method, params);
        }
    }

    private static class MethodInfo extends MethodCall {
        public String type = "info";
        public String info;

        public MethodInfo(String requester, Class clazz, String method, Object... params) {
            super(requester, clazz, method, params);
        }
    }

    private static class MethodError extends MethodCall {
        public String type = "errr";
        public String info;
        public String throwable;

        public MethodError(String requester, Class clazz, String method, Object... params) {
            super(requester, clazz, method, params);
        }
    }

}
