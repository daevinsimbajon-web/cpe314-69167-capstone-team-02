package com.triviagame.online;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.io.IOException;

public class StrictInteger extends JsonDeserializer<Integer> {
    public static class RetryAllowance extends StrictInteger {
        @Override public Integer getAbsentValue(DeserializationContext context){return 0;}
        @Override public Integer getNullValue(DeserializationContext context) throws JsonMappingException {throw InvalidFormatException.from(context.getParser(),"Retry chances must be Off, 1, 2 or 3",null,Integer.class);}
    }
    @Override public Integer deserialize(JsonParser parser,DeserializationContext context) throws IOException {
        if(parser.currentToken()!=JsonToken.VALUE_NUMBER_INT)throw InvalidFormatException.from(parser,"Use a whole number",parser.getText(),Integer.class);
        return parser.getIntValue();
    }
}
