package com.triviagame.online.rooms;

import org.springframework.stereotype.Service;

@Service
public class ScoringService {
    public static final String CURRENT="retry-v2";
    public int calculateCorrectAnswerPoints(int retriesUsedOnQuestion){
        return switch(retriesUsedOnQuestion){case 0->10;case 1->7;case 2->4;case 3->2;default->throw new IllegalArgumentException("Retry usage must be between 0 and 3");};
    }
    public int correctPoints(String version,int retriesUsed,int historicalMaximum){return CURRENT.equals(version)?calculateCorrectAnswerPoints(retriesUsed):historicalMaximum;}
    public int maximum(String version,int historicalMaximum){return CURRENT.equals(version)?10:historicalMaximum;}
}
