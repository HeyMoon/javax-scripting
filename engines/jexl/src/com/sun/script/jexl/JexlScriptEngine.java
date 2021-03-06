/*
 * Copyright 2006 Sun Microsystems, Inc. All rights reserved. 
 * Use is subject to license terms.
 *
 * Redistribution and use in source and binary forms, with or without modification, are 
 * permitted provided that the following conditions are met: Redistributions of source code 
 * must retain the above copyright notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice, this list of 
 * conditions and the following disclaimer in the documentation and/or other materials 
 * provided with the distribution. Neither the name of the Sun Microsystems nor the names of 
 * is contributors may be used to endorse or promote products derived from this software 
 * without specific prior written permission. 

 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY 
 * AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER 
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR 
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR 
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON 
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * JexlScriptEngine.java
 * @author A. Sundararajan
 */

package com.sun.script.jexl;

import javax.script.*;
import java.io.*;
import java.util.*;
import org.apache.commons.jexl.*;

public class JexlScriptEngine extends AbstractScriptEngine 
        implements Compilable {
    // my factory, may be null
    private ScriptEngineFactory factory;

    // my implementation for CompiledScript
    private class JexlCompiledScript extends CompiledScript {
        private Expression expr;

        JexlCompiledScript (Expression expr) {
            this.expr = expr;
        }

        public ScriptEngine getEngine() {
            return JexlScriptEngine.this;
        }

        public Object eval(ScriptContext ctx) throws ScriptException {
            return evalExpr(expr, ctx);
        }
    }

    public CompiledScript compile (String script) throws ScriptException {
        Expression expr = compileExpr(script);
        return new JexlCompiledScript(expr);
    }

    public CompiledScript compile (Reader reader) throws ScriptException {
        return compile(readFully(reader));
    }

    public Object eval(String str, ScriptContext ctx) 
                       throws ScriptException {	
        Expression expr = compileExpr(str);
        return evalExpr(expr, ctx);
    }

    public Object eval(Reader reader, ScriptContext ctx)
                       throws ScriptException {
        return eval(readFully(reader), ctx);
    }

    public ScriptEngineFactory getFactory() {
	synchronized (this) {
	    if (factory == null) {
	    	factory = new JexlScriptEngineFactory();
	    }
        }
	return factory;
    }

    public Bindings createBindings() {
        return new SimpleBindings();
    }

    void setFactory(ScriptEngineFactory factory) {
        this.factory = factory;
    }

    private Expression compileExpr(String str) throws ScriptException {
        try {
            return ExpressionFactory.createExpression(str);
        } catch (Exception exp) {
            throw new ScriptException(exp);
        }
    }

    private Object evalExpr(Expression expr, final ScriptContext ctx) 
                            throws ScriptException {
        // JSR-223 requirement
        ctx.setAttribute("context", ctx, ScriptContext.ENGINE_SCOPE);
        try {
            return expr.evaluate(new JexlContext() {
                public Map getVars() {
                    return new Map() {
                        public int size() {
                            Bindings b = ctx.getBindings(ScriptContext.ENGINE_SCOPE);
                            return b.size();
                        }                        

                        public boolean isEmpty() {
                            Bindings b = ctx.getBindings(ScriptContext.ENGINE_SCOPE);
                            return b.isEmpty();
                        }
                        
                        public boolean containsKey(Object key) {
                            if (key instanceof String) {
                                return ctx.getAttributesScope((String)key) != -1;
                            } else {
                                return false;
                            }
                        }

                        public boolean containsValue(Object value) {
                            for (int scope : ctx.getScopes()) {
                                Bindings b = ctx.getBindings(scope);
                                if (b != null) {
                                    return b.containsValue(value);
                                }
                            }
                            return false;
                        }
                        public Object get(Object key) {
                            if (key instanceof String) {
                                String name = (String) key;
                                int scope = ctx.getAttributesScope(name);
                                if (scope != -1) {
                                    return ctx.getAttribute(name, scope);
                                }                           
                            }
                            return null;
                        }

                        public Object put(Object key, Object value) {
                            if (!(key instanceof String)) return null;
                            String name = (String) key;
                            int scope = ctx.getAttributesScope(name);
                            if (scope == -1) {
                                scope = ScriptContext.ENGINE_SCOPE;
                            }
                            Object old = ctx.getAttribute(name, scope);
                            ctx.setAttribute(name, value, scope);
                            return old;
                        }

                        public Object remove(Object key) {
                            if (!(key instanceof String)) return null;
                            String name = (String) key;
                            int scope = ctx.getAttributesScope(name);
                            if (scope == -1) {
                                scope = ScriptContext.ENGINE_SCOPE;
                            }
                            Object old = ctx.getAttribute(name, scope);
                            ctx.removeAttribute(name, scope);
                            return old;
                        }

                        public void putAll(Map m) {
                            Bindings b = ctx.getBindings(ScriptContext.ENGINE_SCOPE);
                            b.putAll(m);
                        }

                        public void clear() {
                            Bindings b = ctx.getBindings(ScriptContext.ENGINE_SCOPE);
                            b.clear();
                        }

                        public Set keySet() {
                            Bindings b = ctx.getBindings(ScriptContext.ENGINE_SCOPE);
                            return b.keySet();
                        }

                        public Collection values() {
                            Bindings b = ctx.getBindings(ScriptContext.ENGINE_SCOPE);
                            return b.values();
                        }

                        public Set entrySet() {
                            Bindings b = ctx.getBindings(ScriptContext.ENGINE_SCOPE);
                            return b.entrySet();
                        }
                    };
                }

                public void setVars(Map vars) {
                    ctx.setBindings(new SimpleBindings(vars), ScriptContext.ENGINE_SCOPE);
                }
            });
        } catch (Exception exp) {
            throw new ScriptException(exp);
        }
    }

    private String readFully(Reader reader) throws ScriptException { 
        char[] arr = new char[8*1024]; // 8K at a time
        StringBuilder buf = new StringBuilder();
        int numChars;
        try {
            while ((numChars = reader.read(arr, 0, arr.length)) > 0) {
                buf.append(arr, 0, numChars);
            }
        } catch (IOException exp) {
            throw new ScriptException(exp);
        }
        return buf.toString();
    }
}
