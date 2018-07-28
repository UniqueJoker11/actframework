package act.inject.util;

/*-
 * #%L
 * ACT Framework
 * %%
 * Copyright (C) 2014 - 2017 ActFramework
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import act.Act;
import act.app.App;
import act.app.data.StringValueResolverManager;
import com.alibaba.fastjson.JSON;
import org.osgl.$;
import org.osgl.exception.UnexpectedException;
import org.osgl.inject.BeanSpec;
import org.osgl.inject.Genie;
import org.osgl.inject.Injector;
import org.osgl.inject.ValueLoader;
import org.osgl.logging.LogManager;
import org.osgl.logging.Logger;
import org.osgl.storage.ISObject;
import org.osgl.storage.impl.SObject;
import org.osgl.util.E;
import org.osgl.util.IO;
import org.osgl.util.S;
import org.osgl.util.TypeReference;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class ResourceLoader<T> extends ValueLoader.Base<T> {

    protected static final Logger LOGGER = LogManager.get(ResourceLoader.class);

    protected Object resource;

    @Override
    protected void initialized() {
        String path = (String) options.get("value");
        E.unexpectedIf(S.blank(path), "resource path not specified");
        boolean trimLeadingSlash = !(Boolean) options.get("skipTrimLeadingSlash");
        while (trimLeadingSlash && path.startsWith("/")) {
            path = path.substring(1);
        }
        E.unexpectedIf(S.blank(path), "resource path not specified");
        resource = load(path, spec);
    }

    @Override
    public T get() {
        return (T) resource;
    }

    private static Injector injector = Genie.create();

    /**
     * A static method to load resource content
     *
     * If any exception encountered during resource load, this method returns `null`
     *
     * @param path the relative path to the resource
     * @param type the return value type
     * @param <T> generic type of return value
     * @return loaded resource or `null` if exception encountered.
     */
    public static <T> T load(String path, Class<T> type) {
        return load(path, type, false);
    }

    public static <T> T load(String path, Class<T> type, boolean ignoreResourceNotFound) {
        return load(path, BeanSpec.of(type, injector), ignoreResourceNotFound);
    }

    /**
     * A static method to load resource content
     *
     * If any exception encountered during resource load, this method returns `null`
     *
     * @param path the relative path to the resource
     * @param typeReference the return value type
     * @param <T> generic type of return value
     * @return loaded resource or `null` if exception encountered.
     */
    public static <T> T load(String path, TypeReference<T> typeReference) {
        return load(path, typeReference, false);
    }

    public static <T> T load(String path, TypeReference<T> typeReference, boolean ignoreResourceNotFound) {
        BeanSpec spec = BeanSpec.of(typeReference.getType(), injector);
        return load(path, spec, ignoreResourceNotFound);
    }


    /**
     * Load resource content from given path into variable with
     * type specified by `spec`.
     *
     * @param resourcePath the resource path
     * @param spec {@link BeanSpec} specifies the return value type
     * @return the resource content in a specified type or `null` if resource not found
     * @throws UnexpectedException if return value type not supported
     */
    public static <T> T load(String resourcePath, BeanSpec spec) {
        return load(resourcePath, spec, false);
    }

    public static <T> T load(String resourcePath, BeanSpec spec, boolean ignoreResourceNotFound) {
        return $.cast(_load(resourcePath, spec, ignoreResourceNotFound));
    }

    protected static Object _load(String resourcePath, BeanSpec spec, boolean ignoreResourceNotFound) {
        URL url = loadResource(resourcePath);
        if (null == url) {
            if (!ignoreResourceNotFound) {
                LOGGER.warn("resource not found: " + resourcePath);
            }
            return null;
        }
        Class<?> rawType = spec.rawType();
        if (URL.class == rawType) {
            return url;
        }
        if (rawType.isArray()) {
            if (byte[].class == rawType) {
                return readContent(url);
            }
            Class<?> componentType = rawType.getComponentType();
            if (componentType.isArray()) {
                Class<?> subComponentType = componentType.getComponentType();
                boolean isString = String.class == subComponentType;
                boolean isPrimitive = !isString && $.isPrimitiveType(subComponentType);
                boolean isWrapper = !isPrimitive && !isString && $.isWrapperType(subComponentType);
                if (isString || isPrimitive || isWrapper) {
                    List<String> lines = IO.readLines(url);
                    int len = lines.size();
                    Object a2 = Array.newInstance(componentType, len);
                    for (int i = 0; i < len; ++i) {
                        String line = lines.get(i);
                        List<String> elements = S.fastSplit(line, ",");
                        int len2 = elements.size();
                        Object a = Array.newInstance(subComponentType, len2);
                        Array.set(a2, i, a);
                        for (int j = 0; j < len2; ++j) {
                            Object e = $.convert(elements.get(j)).to(subComponentType);
                            if (isPrimitive) {
                                if (int.class == subComponentType) {
                                    Array.setInt(a, j, (Integer) e);
                                } else if (double.class == subComponentType) {
                                    Array.setDouble(a, j, (Double) e);
                                } else if (long.class == subComponentType) {
                                    Array.setLong(a, j, (Long) e);
                                } else if (float.class == subComponentType) {
                                    Array.setFloat(a, j, (Float) e);
                                } else if (boolean.class == subComponentType) {
                                    Array.setBoolean(a, j, (Boolean) e);
                                } else if (short.class == subComponentType) {
                                    Array.setShort(a, j, (Short) e);
                                } else if (byte.class == subComponentType) {
                                    Array.setByte(a, j, (Byte) e);
                                } else if (char.class == subComponentType) {
                                    Array.setChar(a, j, (Character) e);
                                } else {
                                    throw E.unsupport("Sub component type not supported: " + subComponentType.getName());
                                }
                            } else {
                                Array.set(a, j, e);
                            }
                        }
                    }
                    return a2;
                } else {
                    throw E.unsupport("Sub component type not supported: " + subComponentType.getName());
                }
            } else {
                List<String> lines = IO.readLines(url);
                if (String.class == componentType) {
                    return lines.toArray(new String[lines.size()]);
                }
                Object array = Array.newInstance(componentType, lines.size());
                return $.map(lines).to(array);
            }
        }
        boolean isJson = resourcePath.endsWith(".json");
        if (isJson) {
            String content = IO.readContentAsString(url);
            content = content.trim();
            Object o = content.startsWith("[") ? JSON.parseArray(content) : JSON.parseObject(content);
            return $.map(o).to(rawType);
        }
        boolean isYaml = !isJson && (resourcePath.endsWith(".yml") || resourcePath.endsWith(".yaml"));
        if (isYaml) {
            Object o = new Yaml().load(IO.readContentAsString(url));
            return $.map(o).to(rawType);
        } else if (String.class == rawType) {
            return IO.readContentAsString(url);
        } else if (List.class.equals(rawType)) {
            List<Type> typeParams = spec.typeParams();
            List<String> lines = IO.readLines(url);
            if (!typeParams.isEmpty()) {
                if (String.class == typeParams.get(0)) {
                    return lines;
                }
                Type typeParam = typeParams.get(0);
                if (typeParam instanceof Class) {
                    List list = new ArrayList(lines.size());
                    for (String line : lines) {
                        list.add($.convert(line).to((Class) typeParam));
                    }
                    return list;
                }
                throw E.unsupport("List element type not supported: " + typeParam);
            }
        } else if (Map.class.isAssignableFrom(rawType)) {
            if (resourcePath.endsWith(".properties")) {
                Properties properties = IO.loadProperties(url);
                if (Properties.class == rawType || Properties.class.isAssignableFrom(rawType)) {
                    return properties;
                }
                return $.map(properties).to(rawType);
            }
        } else if (Collection.class.isAssignableFrom(rawType)) {
            List<Type> typeParams = spec.typeParams();
            if (!typeParams.isEmpty()) {
                Collection col = (Collection)Act.getInstance(rawType);
                if (String.class == typeParams.get(0)) {
                    col.addAll(IO.readLines(url));
                    return col;
                } else {
                    StringValueResolverManager resolverManager = Act.app().resolverManager();
                    try {
                        Class componentType = spec.componentSpec().rawType();
                        List<String> stringList = IO.readLines(url);
                        for (String line : stringList) {
                            col.add(resolverManager.resolve(line, componentType));
                        }
                    } catch (RuntimeException e) {
                        throw new UnexpectedException("return type not supported: " + spec);
                    }
                }
            }
        } else if (ByteBuffer.class == rawType) {
            byte[] ba = readContent(url);
            ByteBuffer buffer = ByteBuffer.allocateDirect(ba.length);
            buffer.put(ba);
            buffer.flip();
            return buffer;
        } else if (Path.class.isAssignableFrom(rawType)) {
            try {
                return Paths.get(url.toURI());
            } catch (URISyntaxException exception) {
                throw E.unexpected(exception);
            }
        } else if (File.class.isAssignableFrom(rawType)) {
            return new File(url.getFile());
        } else if (ISObject.class.isAssignableFrom(rawType)) {
            return SObject.of(readContent(url));
        } else if (InputStream.class == rawType) {
            return IO.is(url);
        } else if (Reader.class == rawType) {
            return new InputStreamReader(IO.is(url));
        }
        String content = IO.readContentAsString(url);
        try {
            return Act.app().resolverManager().resolve(IO.readContentAsString(url), rawType);
        } catch (RuntimeException e) {
            throw new UnexpectedException("return type not supported: " + spec);
        }
    }

    private static byte[] readContent(URL url) {
        return IO.readContent(IO.is(url));
    }

    private static URL loadResource(String path) {
        App app = Act.app();
        if (null == app || null == app.classLoader()) {
            return ResourceLoader.class.getClassLoader().getResource(path);
        } else {
            return app.classLoader().getResource(path);
        }
    }
}
