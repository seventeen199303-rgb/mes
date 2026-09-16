package com.wyy.system.mapper;

import com.wyy.system.domain.UserTask;

import java.util.List;

public interface UserTaskMapper {

    public List<UserTask> listTodoList(String userName);

    public List<UserTask> listFinishedList(String userName);

}
