package de.symeda.sormas.app.core.async;

import de.symeda.sormas.app.core.BoolResult;

public class AsyncTaskResult<T> {
    private T result;
    private Exception error;
    private BoolResult resultStatus;

    public T getResult() {
        return result;
    }

    public Exception getError() {
        return error;
    }

    public BoolResult getResultStatus() {
        return resultStatus;
    }

    public AsyncTaskResult(BoolResult resultStatus, T result) {
        super();
        this.result = result;
        this.resultStatus = resultStatus;
    }

    public AsyncTaskResult(Exception error) {
        super();
        this.error = error;
        this.resultStatus = new BoolResult(false, error.getMessage());
    }
}