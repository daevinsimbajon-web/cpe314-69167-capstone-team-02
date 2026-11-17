package com.triviagame.online;

import com.triviagame.online.auth.AvatarConfig;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AvatarConfigTest {
    @Test void optionalSpeciesIsBackwardCompatibleAndNewIdentitiesAreVaried(){
        var id=UUID.fromString("00000000-0000-0000-0000-000000000001");
        var legacy=AvatarConfig.forUser(id);assertThat(legacy.species()).isNull();assertThat(legacy.stored()).doesNotContain("species");
        assertThat(AvatarConfig.read(legacy.stored(),id)).isEqualTo(legacy);
        var species=new java.util.HashSet<Integer>();
        for(int i=0;i<100;i++){var bird=AvatarConfig.forNewUser(new UUID(0,i));assertThat(bird.valid()).isTrue();species.add(bird.species());assertThat(AvatarConfig.read(bird.stored(),id)).isEqualTo(bird);}
        assertThat(species).hasSize(8);
    }
    @Test void deterministicBoundedParametersRoundTripAndHavePracticalVariety(){
        var combinations=new java.util.HashSet<String>();
        for(int i=0;i<1000;i++){UUID id=new UUID(0,i);var owl=AvatarConfig.forUser(id);assertThat(owl.valid()).isTrue();assertThat(AvatarConfig.read(owl.stored(),id)).isEqualTo(owl);assertThat(AvatarConfig.forUser(id)).isEqualTo(owl);combinations.add(owl.stored());}
        assertThat(combinations.size()).isGreaterThan(900);
    }
    @Test void missingOrInvalidStoredLegacyParametersUseStableFallback(){UUID id=UUID.fromString("00000000-0000-0000-0000-000000000001");assertThat(AvatarConfig.read(null,id)).isEqualTo(AvatarConfig.forUser(id));assertThat(AvatarConfig.read("{\"version\":99}",id)).isEqualTo(AvatarConfig.forUser(id));assertThat(AvatarConfig.read("broken",id)).isEqualTo(AvatarConfig.forUser(id));}
}
