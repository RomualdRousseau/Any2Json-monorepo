
package com.github.romualdrousseau.any2json.v2.layex.operations;

import java.util.LinkedList;

import com.github.romualdrousseau.any2json.v2.layex.Context;
import com.github.romualdrousseau.any2json.v2.layex.LayexMatcher;
import com.github.romualdrousseau.any2json.v2.layex.IStream;
import com.github.romualdrousseau.any2json.v2.layex.ISymbol;

public class Group implements LayexMatcher {

    public Group(LinkedList<LayexMatcher> stack, int group) {
      this.a = stack.pop();
      this.group = group;
    }

    @Override
    public <S extends ISymbol, C> boolean match(IStream<S, C> stream, Context<S> context) {
        if(context != null) {
            context.setGroup(this.group);
        }
        return this.a.match(stream, context);
    }

    @Override
    public String toString() {
      return "GROUP(" + this.a + ")";
    }

    private LayexMatcher a;
    private int group;
  }
