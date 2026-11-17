package com.triviagame.online.analytics;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ExportSigning {
    private final ObjectMapper json;private final JdbcTemplate jdbc;
    public ExportSigning(ObjectMapper json,JdbcTemplate jdbc){this.json=json;this.jdbc=jdbc;}
    public AnalyticsExport sign(UUID user,AnalyticsExport data) {
        return new AnalyticsExport(data.format(),data.schemaVersion(),data.exportedAt(),data.player(),data.games(),
            new AnalyticsExport.IdentityProof(user.toString(),signature(user.toString(),json.valueToTree(data))));
    }
    public UUID verify(JsonNode raw) {
        JsonNode proof=raw.get("identityProof");if(proof==null||proof.isNull())return null;
        try {
            String account=proof.path("accountId").asText(),supplied=proof.path("signature").asText();
            if(supplied.length()!=64||!MessageDigest.isEqual(signature(account,raw).getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8)))
                throw new IllegalArgumentException("Invalid signed export");
            UUID user=UUID.fromString(account);
            String pseudonym=UUID.nameUUIDFromBytes(("hootly.analytics:"+user).getBytes(StandardCharsets.UTF_8)).toString();
            if(!pseudonym.equals(raw.path("player").path("exportPlayerId").asText()))throw new IllegalArgumentException("Identity does not match export");
            return jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id=? AND deleted_at IS NULL",Integer.class,user)==1?user:null;
        }catch(IllegalArgumentException invalid){throw invalid;}catch(Exception invalid){throw new IllegalArgumentException("Invalid signed export",invalid);}
    }
    private String signature(String account,JsonNode raw) {
        try {
            ObjectNode payload=((ObjectNode)raw).deepCopy();payload.remove("identityProof");
            byte[] key=Base64.getDecoder().decode(jdbc.queryForObject("SELECT secret FROM analytics_signing_keys WHERE id=1",String.class));
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((account+"\n"+canonical(payload)).getBytes(StandardCharsets.UTF_8)));
        }catch(Exception failure){throw new IllegalStateException("Cannot sign statistics export",failure);}
    }
    private JsonNode canonical(JsonNode node) {
        if(node.isObject()){ObjectNode sorted=json.createObjectNode();var keys=new TreeSet<String>();node.fieldNames().forEachRemaining(keys::add);keys.forEach(k->sorted.set(k,canonical(node.get(k))));return sorted;}
        if(node.isArray()){ArrayNode array=json.createArrayNode();node.forEach(n->array.add(canonical(n)));return array;}return node;
    }
}
