package com.scripty.service;

import java.util.List;

public interface AuditionService {

    void setAuditionsForActorInProject(Integer actorId, Integer projectId, List<Integer> characterIds);
}
