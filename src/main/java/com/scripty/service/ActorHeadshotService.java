package com.scripty.service;

import com.scripty.dto.Actor;
import java.util.Optional;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface ActorHeadshotService {

    void updateHeadshot(Actor actor, MultipartFile headshot, boolean removeHeadshot);

    void deleteHeadshot(Actor actor);

    Optional<Resource> loadHeadshot(Integer actorId);

    Optional<String> getContentType(Integer actorId);

    boolean hasHeadshot(Actor actor);
}
