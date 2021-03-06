package com.gongsj.app.service.impl;

import com.gongsj.app.entity.SmsMessageUser;
import com.gongsj.app.exception.ObjectAlreadyExistException;
import com.gongsj.app.repository.SmsMessageUserRepository;
import com.gongsj.app.service.SmsMessageUserService;
import com.gongsj.core.domain.MessageUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;

@Slf4j
@Service
public class SmsMessageUserServiceImpl implements SmsMessageUserService {

    private final static Map<String, SmsMessageUser> cachedMessageUsers = new ConcurrentHashMap<>(60);

    private final SmsMessageUserRepository messageUserRepository;

    public SmsMessageUserServiceImpl(SmsMessageUserRepository messageUserRepository) {
        this.messageUserRepository = messageUserRepository;

        cachedMessageUsers.putAll(messageUserRepository.findByUserStatusNot(MessageUser.SmsUserStatus.DISABLED)
                .stream()
                .collect(toMap(MessageUser::getId, a->a)));
    }


    @Override
    public List<SmsMessageUser> findByUserStatusNot(MessageUser.SmsUserStatus userStatus) {
        Assert.notNull(userStatus, "userStatus is null");
        return messageUserRepository.findByUserStatusNot(userStatus);
    }

    @Override
    public SmsMessageUser save(SmsMessageUser messageUser) {
        Assert.notNull(messageUser, "messageUser is null");
        String id = messageUser.getId();
        Assert.hasText(id, "id is empty or null");
        //先从内存中找，没有的话从数据库中查找
        MessageUser oldSmsMessageUser = Optional.ofNullable(cachedMessageUsers.get(id)).orElse(get(id));
        Assert.notNull(oldSmsMessageUser, "The MessageUser with id=" + id + " does not exists");
        messageUser.getUsable();
        //保存到内存
        cachedMessageUsers.put(id, messageUser);
        //保存到数据库
        return messageUserRepository.save(messageUser);
    }

    @Override
    public void create(SmsMessageUser messageUser) {
        String id = messageUser.getId();
        //这里必须要求ID不为空
        Assert.hasText(id, "The id of the object to be saved must be specified");
        //id不能重复
        if (exist(id)) throw new ObjectAlreadyExistException("The MessageUser with id=" + id + " already exists");
        cachedMessageUsers.put(id, messageUser);
        messageUserRepository.save(messageUser);
    }

    @Override
    public SmsMessageUser get(String id) {
        Assert.hasText(id, "id is empty or null");
        return Optional.ofNullable(cachedMessageUsers.get(id))
                .orElse(messageUserRepository.findOne(id));
    }

    @Override
    public Page<SmsMessageUser> findByPage(Pageable page) {
        Assert.notNull(page, "Pageable is null");
        return messageUserRepository.findAll(page);
    }

    @Override
    public List<SmsMessageUser> findAll() {
        return new ArrayList<>(cachedMessageUsers.values());
    }

    @Override
    public List<SmsMessageUser> findAll(Set<String> ids) {

        return cachedMessageUsers.keySet().stream()
                .filter(ids::contains)
                .map(cachedMessageUsers::get)
                .collect(Collectors.toList());
    }

    @Override
    public boolean exist(String id) {
        Assert.hasText(id, "id is empty or null");
        return cachedMessageUsers.get(id) != null || messageUserRepository.exists(id);
    }

    @Override
    public void remove(String id) {
        Assert.hasText(id, "id is empty or null");
        cachedMessageUsers.remove(id);
        messageUserRepository.delete(id);
    }
}
