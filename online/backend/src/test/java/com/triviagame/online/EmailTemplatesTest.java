package com.triviagame.online;

import com.triviagame.online.mail.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EmailTemplatesTest {
    final EmailTemplates templates=new EmailTemplates();
    EmailMessage example(EmailKind kind){return new EmailMessage(UUID.randomUUID(),"preview@example.com","<Player & friend>",kind,kind==EmailKind.PASSWORD_CHANGED?null:"483627",Instant.parse("2026-11-17T00:30:00Z"));}
    @Test void templatesEscapeNamesAndContainAccessibleInlineBranding(){
        for(EmailKind kind:EmailKind.values()){
            var rendered=templates.render(example(kind));assertThat(rendered.html()).contains("&lt;Player &amp; friend&gt;","cid:hootly-owl","role=\"presentation\"","#0D1224").doesNotContain("<script","localhost","<Player");
            assertThat(rendered.text()).contains("Hootly");
            if(kind==EmailKind.PASSWORD_CHANGED)assertThat(rendered.text()).contains("17 Nov 2026, 00:30 UTC").doesNotContain("483627");else assertThat(rendered.text()).contains("483627","10 minutes");
        }
    }
    @Test void writePreviews() throws Exception {
        Path directory=Path.of("target/email-previews");Files.createDirectories(directory);
        for(EmailKind kind:EmailKind.values())Files.writeString(directory.resolve(kind.name().toLowerCase(Locale.ROOT)+".html"),templates.render(example(kind)).html().replace("cid:hootly-owl","hootly-owl.png"));
        try(var png=getClass().getResourceAsStream("/mail/hootly-owl.png")){Files.copy(png,directory.resolve("hootly-owl.png"),StandardCopyOption.REPLACE_EXISTING);}
    }
}
