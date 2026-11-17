package com.triviagame.online.rooms;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface RoomRepository extends JpaRepository<QuizRoom,UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from QuizRoom r where r.code=:code")
    Optional<QuizRoom> lockByCode(@Param("code") String code);
    Optional<QuizRoom> findByCode(String code);
}
