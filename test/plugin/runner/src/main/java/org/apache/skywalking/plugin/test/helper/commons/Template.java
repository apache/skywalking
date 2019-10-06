package org.apache.skywalking.plugin.test.helper.commons;

import com.google.common.collect.Maps;

import java.io.*;
import java.util.Map;
import java.util.Stack;

/**
 * Author Daming
 * Email zteny@foxmail.com
 **/
public class Template {
    public static Writer generate(Reader reader, Map<String, String> data) throws IOException {
        StringWriter writer = new StringWriter();
        char[] buffer = new char[128];

        int dollar = -1;
        int offset = 0;
        int braceNum = 0;
        boolean skipper = false;
        Stack<Node> stack = new Stack<>();
        int length = reader.read(buffer);
        while (length >= 0) {
            for (int pos=0; pos<length; pos++) {
                final char c = buffer[pos];
                switch (c) {
                    case '$' :
                        if (buffer[pos+1] == '$') {
                            pos++;
                            continue;
                        }
                        if (offset > 0) {
                            writer.write(buffer, offset, pos - offset);
                        }
                        offset = pos;
                        dollar = offset;
                        break;
                    case '{' :
                        if (++braceNum > 1) {
                            continue;
                        }
                        if (dollar < 0) {
                            writer.write(buffer, offset, pos - offset);
                        }
                        dollar = -1;
                        if (buffer[pos + 1] == '{') {
                            skipper = true;
                            break;
                        }

                        offset = pos + 1;
                        stack.push(new Node(c, pos));
                        break;
                    case '}' :
                        if (--braceNum > 0) {
                            continue;
                        }

                        if (skipper) {
                            writer.write(new String(buffer, offset+1, pos - offset));
                            skipper = false;
                        }
                        else if (stack.peek().type == '{') {
                            Node node = stack.pop();
                            writer.write(data.get(new String(buffer, offset, pos - offset)));
                        }
                        offset = pos + 1;
                        break;
                    case ' ' :
                        if (dollar > 0) {
                            writer.write(data.get(new String(buffer, dollar+1, pos - dollar-1)));
                            dollar = -1;
                            offset = pos;
                        }
                    default : {
                    }
                }
            }
            if (offset < length) {
                writer.write(buffer, offset, length - offset);
            }
            if (!stack.isEmpty() || braceNum > 0) {
                System.out.println("<<<<<<<<<<<<<<<<<<<<<");
            }

            length = reader.read(buffer);
            offset += length;
        }
        writer.flush();
        return writer;
    }

    static class Node {
        int offset = 0;
        char type = ' ';
        Node (char type, int offset) {
            this.type = type;
            this.offset = offset;
        }
    }

    public static void main(String[] args) throws IOException {
        Map<String, String> data = Maps.newHashMap();
        data.put("name", "daming");
        data.put("first", "1");
        data.put("second", "2");
        Writer writer = generate(new StringReader("Hello, {name} ${first} $second {second} {{// name {name} }} asd {second} $name {{daming}} A"), data);
        System.out.println("what:" + writer.toString());
        System.out.println(">>>>>>>>>>>>>>>>>>>>>");
    }
}
