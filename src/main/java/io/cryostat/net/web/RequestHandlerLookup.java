/*
 * Copyright The Cryostat Authors
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software (each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 * The above copyright notice and either this complete permission notice or at
 * a minimum a reference to the UPL must be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.cryostat.net.web;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.cryostat.net.web.http.RequestHandler;

import io.vertx.ext.web.Route;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

public class RequestHandlerLookup {

    private final Set<Pair<RequestHandler, Route>> set = new HashSet<>();

    void addPair(RequestHandler handler, Route route) {
        set.add(Pair.of(handler, route));
    }

    public Optional<RequestHandler> forRequestUri(URI uri) {
        System.out.println(String.format("Looking up handler for URI %s", uri));
        return forPath(uri.getRawPath());
    }

    public Optional<RequestHandler> forPath(String path) {
        System.out.println(String.format("Looking up handler for path %s", path));
        // FIXME do Pattern.compile for routes when they are added and cache the created Patterns
        // for reuse
        // FIXME using reflection is disgusting - would be better to replicate the way that Vertx
        // processes paths with parameters into regexes. We would then also not need the WebServer
        // to feed us pairs of Route/RequestHandler, we would just need the set of RequestHandlers
        for (Pair<RequestHandler, Route> pair : set) {
            Route route = pair.getRight();
            RequestHandler handler = pair.getLeft();

            if (StringUtils.isBlank(route.getPath())) {
                System.out.println(
                        String.format(
                                "Skipping %s because the path is blank",
                                handler.getClass().getName()));
                continue;
            }
            System.out.println(
                    String.format(
                            "Checking %s - path is %s",
                            handler.getClass().getName(), route.getPath()));

            if (!route.isRegexPath()) {
                if (Objects.equals(route.getPath(), path)) {
                    System.out.println(
                            String.format("handler has a non-expression path - matched"));
                    return Optional.of(handler);
                } else {
                    System.out.println(
                            String.format("handler has a non-matching, non-expression path"));
                    continue;
                }
            }

            try {
                Field f = (Field) route.getClass().getDeclaredField("state");
                f.setAccessible(true);
                Class<?> routeStateKlazz = Class.forName("io.vertx.ext.web.impl.RouteState");
                Method getPattern = routeStateKlazz.getMethod("getPattern");
                getPattern.setAccessible(true);
                Pattern pattern = (Pattern) getPattern.invoke(f.get(route));
                Matcher m = pattern.matcher(path);
                if (m.matches()) {
                    System.out.println("handler has a matching regex path");
                    return Optional.of(handler);
                }
                System.out.println("handler has a non-matching path");
            } catch (ClassNotFoundException
                    | NoSuchFieldException
                    | NoSuchMethodException
                    | SecurityException
                    | IllegalAccessException
                    | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }

        return Optional.empty();
    }

    // STOLEN FROM VERTX

    // private synchronized void setPath(String path) {
    //   // See if the path contains ":" - if so then it contains parameter capture groups and we
    // have to generate
    //   // a regex for that
    //   if (path.charAt(path.length() - 1) != '*') {
    //     state = state.setExactPath(true);
    //     state = state.setPath(path);
    //   } else {
    //     state = state.setExactPath(false);
    //     state = state.setPath(path.substring(0, path.length() - 1));
    //   }

    //   if (path.indexOf(':') != -1) {
    //     createPatternRegex(path);
    //   }

    //   state = state.setPathEndsWithSlash(state.getPath().endsWith("/"));
    // }

    // private synchronized void createPatternRegex(String path) {
    //   // escape path from any regex special chars
    //   path = RE_OPERATORS_NO_STAR.matcher(path).replaceAll("\\\\$1");
    //   // allow usage of * at the end as per documentation
    //   if (path.charAt(path.length() - 1) == '*') {
    //     path = path.substring(0, path.length() - 1) + "(?<rest>.*)";
    //     state = state.setExactPath(false);
    //   } else {
    //     state = state.setExactPath(true);
    //   }

    //   // We need to search for any :<token name> tokens in the String and replace them with named
    // capture groups
    //   Matcher m = RE_TOKEN_SEARCH.matcher(path);
    //   StringBuffer sb = new StringBuffer();
    //   List<String> groups = new ArrayList<>();
    //   int index = 0;
    //   while (m.find()) {
    //     String param = "p" + index;
    //     String group = m.group().substring(1);
    //     if (groups.contains(group)) {
    //       throw new IllegalArgumentException("Cannot use identifier " + group + " more than once
    // in pattern string");
    //     }
    //     m.appendReplacement(sb, "(?<" + param + ">[^/]+)");
    //     groups.add(group);
    //     index++;
    //   }
    //   m.appendTail(sb);
    //   path = sb.toString();

    //   state = state.setGroups(groups);
    //   state = state.setPattern(Pattern.compile(path));
    // }
}
