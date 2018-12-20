package com.frostnerd.smokescreen.database.serializers;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.frostnerd.database.orm.Serializer;

/**
 * Copyright Daniel Wolf 2018
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 * <p>
 * development@frostnerd.com
 */
public class LongSerializer extends Serializer<Long> {
    @Override
    protected String serializeValue(@NonNull Long aLong) {
        return aLong.toString();
    }

    @Nullable
    @Override
    public Long deserialize(@NonNull String s) {
        return Long.parseLong(s);
    }

    @Override
    public String serializeNull() {
        return "";
    }
}
