package com.vish.fno.model.strategy;

import com.vish.fno.model.Task;

public interface Strategy {
    void initialise(Task task);
    Task getTask();
    String getTag();
}
