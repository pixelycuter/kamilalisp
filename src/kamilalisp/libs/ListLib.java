package kamilalisp.libs;

import com.google.common.collect.Lists;
import com.google.common.collect.Streams;
import kamilalisp.api.Evaluation;
import kamilalisp.data.*;
import kamilalisp.libs.primitives.list.Sort;
import kamilalisp.data.Matrix;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class ListLib {
    private static <T> List<List<T>> cartesianProduct(List<List<T>> lists) {
        List<List<T>> resultLists = new ArrayList<List<T>>();
        if (lists.size() == 0) {
            resultLists.add(new ArrayList<T>());
            return resultLists;
        } else {
            List<T> firstList = lists.get(0);
            List<List<T>> remainingLists = cartesianProduct(lists.subList(1, lists.size()));
            for (T condition : firstList) {
                for (List<T> remainingList : remainingLists) {
                    ArrayList<T> resultList = new ArrayList<T>();
                    resultList.add(condition);
                    resultList.addAll(remainingList);
                    resultLists.add(resultList);
                }
            }
        }
        return resultLists;
    }

    public static void install(Environment env) {
        env.push("iota", new Atom(new Closure() {
            private List<BigDecimal> iota(long n) {
                List<BigDecimal> result = new ArrayList<>();
                for(long i = 0; i < n; i++)
                    result.add(BigDecimal.valueOf(i));
                return result;
            }

            @Override
            public Atom apply(Executor env, List<Atom> arguments) {
                if(arguments.size() != 1)
                    throw new Error("Invalid invocation to 'iota'.");
                if(arguments.get(0).getType() == Type.NUMBER)
                    return new Atom(new LbcSupplier<>(() ->
                            iota(arguments.get(0).getNumber().get().toBigInteger().intValue())
                                    .stream().map(Atom::new).collect(Collectors.toList())));
                else if(arguments.get(0).getType() == Type.LIST) {
                    return new Atom(new LbcSupplier<>(() -> {
                        List<List<BigDecimal>> iotas = arguments.get(0).getList().get().stream().map(x -> {
                            if(x.getType() != Type.NUMBER)
                                throw new Error("Invalid invocation to 'iota'. Expected a list of numbers.");
                            return iota(x.getNumber().get().toBigInteger().intValue());
                        }).collect(Collectors.toList());

                        return cartesianProduct(iotas)
                                .stream()
                                .map(x -> new Atom(x.stream().map(Atom::new).collect(Collectors.toList())))
                                .collect(Collectors.toList());
                    }));
                }
                throw new Error("Unimplemented");
            }
        }));

        env.push("nth", new Atom(new Closure() {
            @Override
            public Atom apply(Executor env, List<Atom> arguments) {
                if(arguments.size() != 2)
                    throw new Error("Invalid invocation to 'nth'.");
                return new Atom(new LbcSupplier<>(() -> {
                    arguments.get(0).guardType("Argument to 'nth'.", Type.NUMBER);
                    if(arguments.get(1).getType() == Type.LIST) {
                        List<Atom> l = arguments.get(1).getList().get();
                        int n = arguments.get(0).getNumber().get().intValue();
                        if (n < 0 || n >= l.size())
                            throw new Error("Index out of bounds.");
                        return l.get(n).get().get();
                    } else if(arguments.get(1).getType() == Type.STRING_CONSTANT) {
                        String s = arguments.get(1).getStringConstant().get().get();
                        int n = arguments.get(0).getNumber().get().intValue();
                        if (n < 0 || n >= s.length())
                            throw new Error("Index out of bounds.");
                        return new StringConstant("" + s.charAt(n));
                    }

                    throw new Error("Invalid argument to 'nth'.");
                }));
            }
        }));

        env.push("tie", new Atom(new Closure() {
            @Override
            public Atom apply(Executor env, List<Atom> arguments) {
                return new Atom(arguments);
            }
        }));

        env.push("flatten", new Atom(new Closure() {
            public <T> List<T> flat(List<List<T>> list) {
                return list.stream()
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList());
            }

            @Override
            public Atom apply(Executor env, List<Atom> arguments) {
                if(arguments.size() != 1)
                    throw new Error("Invalid invocation to 'flatten'.");
                return new Atom(new LbcSupplier<>(() -> {
                    arguments.get(0).guardType("Argument to 'flatten'.", Type.LIST);
                    List<Atom> l = arguments.get(0).getList().get();
                    return flat(l.stream().map(x -> x.getType() == Type.LIST ? x.getList().get() : List.of(x)).collect(Collectors.toList()));
                }));
            }
        }));

        env.push("reverse", new Atom(new Closure() {
            @Override
            public Atom apply(Executor env, List<Atom> arguments) {
                if(arguments.size() != 1)
                    throw new Error("Invalid invocation to 'reverse'.");
                return new Atom(new LbcSupplier<>(() -> {
                    if(arguments.get(0).getType() == Type.LIST) {
                        List<Atom> l = arguments.get(0).getList().get();
                        List<Atom> r = new ArrayList<>();
                        for (int i = l.size() - 1; i >= 0; i--)
                            r.add(l.get(i));
                        return r;
                    } else if(arguments.get(0).getType() == Type.STRING_CONSTANT) {
                        String s = arguments.get(0).getStringConstant().get().get();
                        String r = "";
                        for (int i = s.length() - 1; i >= 0; i--)
                            r += s.charAt(i);
                        return new StringConstant(r);
                    }

                    throw new Error("Invalid invocation to 'reverse': expected a string or list.");
                }));
            }
        }));

        env.push("rotate", new Atom(new Closure() {
            @Override
            public Atom apply(Executor env, List<Atom> arguments) {
                if(arguments.size() != 2)
                    throw new Error("Invalid invocation to 'rotate'.");
                return new Atom(new LbcSupplier<>(() -> {
                    if(arguments.get(0).getType() == Type.LIST) {
                        List<Atom> l = arguments.get(0).getList().get();
                        int n = arguments.get(1).getNumber().get().intValue();
                        if(n < 0)
                            n += l.size();
                        if(n >= l.size())
                            n %= l.size();
                        List<Atom> r = new ArrayList<>();
                        for (int i = n; i < l.size(); i++)
                            r.add(l.get(i));
                        for (int i = 0; i < n; i++)
                            r.add(l.get(i));
                        return r;
                    } else if(arguments.get(0).getType() == Type.STRING_CONSTANT) {
                        String s = arguments.get(0).getStringConstant().get().get();
                        int n = arguments.get(1).getNumber().get().intValue();
                        if(n < 0)
                            n += s.length();
                        if(n >= s.length())
                            n %= s.length();
                        String r = "";
                        for (int i = n; i < s.length(); i++)
                            r += s.charAt(i);
                        for (int i = 0; i < n; i++)
                            r += s.charAt(i);
                        return new StringConstant(r);
                    }

                    throw new Error("Invalid invocation to 'rotate': expected a string or list.");
                }));
            }
        }));

        env.push("zip", new Atom(new Closure() {
            @Override
            public Atom apply(Executor env, List<Atom> arguments) {
                if(arguments.size() != 2)
                    throw new Error("Invalid invocation to 'zip'.");
                return new Atom(new LbcSupplier<>(() -> {
                    arguments.get(0).guardType("First argument to 'zip'", Type.LIST);
                    arguments.get(1).guardType("Second argument to 'zip'", Type.LIST);
                    List<Atom> a = arguments.get(0).getList().get();
                    List<Atom> b = arguments.get(1).getList().get();
                    return Streams.zip(a.stream(), b.stream(), (x, y) -> new Atom(new LbcSupplier<>(() -> List.of(x, y)))).collect(Collectors.toList());
                }));
            }
        }));

        env.push("first", new Atom(new Closure() {
            @Override
            public Atom apply(Executor env, List<Atom> arguments) {
                if(arguments.size() != 2)
                    throw new Error("Invalid invocation to 'first'.");
                return new Atom(new LbcSupplier<>(() -> {
                    arguments.get(0).guardType("First argument to 'first'", Type.CLOSURE, Type.MACRO);
                    arguments.get(1).guardType("Second argument to 'first'", Type.LIST);
                    Callable c = arguments.get(0).getCallable().get();
                    List<Atom> l = arguments.get(1).getList().get();
                    for(int i = 0; i < l.size(); i++) {
                        Atom a = l.get(i);
                        if (c.apply(env, List.of(a)).coerceBool())
                            return a.get().get();
                    }
                    return Atom.NULL.get().get();
                }));
            }
        }));

        env.push("first-idx", new Atom(new Closure() {
            @Override
            public Atom apply(Executor env, List<Atom> arguments) {
                if(arguments.size() != 2)
                    throw new Error("Invalid invocation to 'first'.");
                return new Atom(new LbcSupplier<>(() -> {
                    arguments.get(0).guardType("First argument to 'first'", Type.CLOSURE, Type.MACRO);
                    arguments.get(1).guardType("Second argument to 'first'", Type.LIST);
                    Callable c = arguments.get(0).getCallable().get();
                    List<Atom> l = arguments.get(1).getList().get();
                    for(int i = 0; i < l.size(); i++) {
                        Atom a = l.get(i);
                        if (c.apply(env, List.of(a)).coerceBool())
                            return new BigDecimal(i);
                    }
                    return Atom.NULL.get().get();
                }));
            }
        }));

        env.push("any", new Atom(new Closure() {
            @Override
            public Atom apply(Executor env, List<Atom> arguments) {
                if(arguments.size() != 2 && arguments.size() != 1)
                    throw new Error("Invalid invocation to 'any'.");
                return new Atom(new LbcSupplier<>(() -> {
                    if(arguments.size() == 2) {
                        arguments.get(0).guardType("First argument to 'any'", Type.CLOSURE, Type.MACRO);
                        arguments.get(1).guardType("Second argument to 'any'", Type.LIST);
                        return arguments.get(1).getList().get().stream().anyMatch(x ->
                                arguments.get(0).getCallable().get().apply(env, Collections.singletonList(x)).coerceBool()
                        ) ? BigDecimal.ONE : BigDecimal.ZERO;
                    } else {
                        arguments.get(0).guardType("First argument to 'any'", Type.LIST);
                        return arguments.get(0).getList().get().stream().anyMatch(Atom::coerceBool) ? BigDecimal.ONE : BigDecimal.ZERO;
                    }
                }));
            }
        }));

        env.push("cdr", new Atom(new Closure() {
            @Override
            public Atom apply(Executor env, List<Atom> arguments) {
                if(arguments.size() != 1)
                    throw new Error("Invalid invocation to 'cdr'.");
                return new Atom(new LbcSupplier<>(() -> {
                    arguments.get(0).guardType("Argument to 'cdr'", Type.LIST);
                    List<Atom> data = arguments.get(0).getList().get();
                    if(data.isEmpty())
                        return Atom.NULL.get().get();
                    else
                        return data.subList(1, data.size());
                }));
            }
        }));

        env.push("str-split", new Atom(new Closure() {
            @Override
            public Atom apply(Executor env, List<Atom> arguments) {
                if(arguments.size() != 2)
                    throw new Error("Invalid invocation to 'str-split'.");
                return new Atom(new LbcSupplier<>(() -> {
                    arguments.get(0).guardType("First argument to 'str-split'", Type.STRING_CONSTANT);
                    arguments.get(1).guardType("Second argument to 'str-split'", Type.STRING_CONSTANT);
                    String s = arguments.get(0).getStringConstant().get().get();
                    String delim = arguments.get(1).getStringConstant().get().get();
                    return Arrays.stream(s.split(delim)).map(x -> new Atom(new StringConstant(x))).collect(Collectors.toList());
                }));
            }
        }));

        env.push("cons", new Atom(new Closure() {
            private List<Atom> cons2(Atom element, Atom list) {
                list.guardType("Second argument to 'cons'", Type.LIST);
                LinkedList<Atom> l = new LinkedList<>(list.getList().get());
                l.addFirst(element);
                return l;
            }

            @Override
            public Atom apply(Executor env, List<Atom> arguments) {
                if(arguments.size() != 2)
                    throw new Error("invalid invocation to 'cons'.");
                else
                    return new Atom(new LbcSupplier(() -> cons2(arguments.get(0), arguments.get(1))));
            }
        }));

        env.push("append", new Atom(new Closure() {
            private List<Atom> append2(Atom list, Atom tail) {
                list.guardType("Argument to 'append'", Type.LIST);
                LinkedList<Atom> l = new LinkedList<Atom>(list.getList().get());
                l.addLast(tail);
                return l;
            }

            @Override
            public Atom apply(Executor env, List<Atom> arguments) {
                if(arguments.size() < 2)
                    throw new Error("invalid invocation to 'append'.");
                if(arguments.size() == 2)
                    return new Atom(new LbcSupplier(() -> append2(arguments.get(0), arguments.get(1))));
                else
                    return new Atom(new LbcSupplier(() -> {
                        arguments.get(0).guardType("Argument to 'append'", Type.LIST);
                        List<Atom> l = new LinkedList<>(arguments.get(0).getList().get());
                        l.addAll(arguments.stream().skip(1).collect(Collectors.toList()));
                        return l;
                    }));
            }
        }));

        env.push("car", new Atom(new Closure() {
            private Atom car(Atom l) {
                l.guardType("Argument to 'car'", Type.LIST);
                List<Atom> data = l.getList().get();
                if(data.isEmpty())
                    return Atom.NULL;
                else
                    return data.get(0);
            }

            @Override
            public Atom apply(Executor env, List<Atom> arguments) {
                if(arguments.size() == 1) {
                    return new Atom(new LbcSupplier(() -> car(arguments.get(0)).get().get()));
                } else if(arguments.size() >= 2) {
                    return new Atom(new LbcSupplier(() -> arguments.stream().map(x -> car(x)).collect(Collectors.toList())));
                } else
                    throw new Error("Invalid invocation to 'car'.");
            }
        }));

        env.push("size", new Atom(new Closure() {
            @Override
            public Atom apply(Executor env, List<Atom> arguments) {
                if(arguments.size() != 1)
                    throw new Error("Invalid invocation to 'size'.");
                return new Atom(new LbcSupplier<>(() -> {
                    arguments.get(0).guardType("Argument to 'size'", Type.LIST, Type.STRING_CONSTANT);

                    if(arguments.get(0).getType() == Type.LIST)
                        return BigDecimal.valueOf(arguments.get(0).getList().get().size());
                    else if(arguments.get(0).getType() == Type.STRING_CONSTANT)
                        return BigDecimal.valueOf(arguments.get(0).getStringConstant().get().get().length());

                    throw new Error("Invalid invocation to 'size'.");
                }));
            }
        }));

        env.push("sort", new Atom(new Sort()));

        env.push("grade-up", new Atom(new Closure() {
            @Override
            public Atom apply(Executor env, List<Atom> arguments) {
                if(arguments.size() < 1)
                    throw new Error("Invalid invocation to 'grade-up'.");
                return new Atom(new LbcSupplier<>(() -> {
                    if(arguments.size() == 1 && arguments.get(0).getType() == Type.LIST) {
                        List<Atom> l = arguments.get(0).getList().get();
                        return IntStream.range(0, l.size()).boxed().sorted(new Comparator<Integer>() {
                            @Override
                            public int compare(Integer o1, Integer o2) {
                                return new AtomComparator().compare(l.get(o1), l.get(o2));
                            }
                        }).map(x -> new Atom(BigDecimal.valueOf(x))).collect(Collectors.toList());
                    } else if(arguments.size() == 2 && arguments.get(0).isCallable() && arguments.get(1).getType() == Type.LIST) {
                        Callable c = arguments.get(0).getCallable().get();
                        List<Atom> l = arguments.get(1).getList().get();
                        return IntStream.range(0, l.size()).boxed().sorted(new Comparator<Integer>() {
                            @Override
                            public int compare(Integer o1, Integer o2) {
                                Atom r = c.apply(env, List.of(l.get(o1), l.get(o2)));
                                r.guardType("'grade-up' comparator", Type.NUMBER);
                                return r.getNumber().get().intValue();
                            }
                        }).map(x -> new Atom(BigDecimal.valueOf(x))).collect(Collectors.toList());
                    } else
                        throw new Error("'grade-up' expects (closure, list) arguments or a list");
                }));
            }
        }));

        env.push("grade-down", new Atom(new Closure() {
            @Override
            public Atom apply(Executor env, List<Atom> arguments) {
                if(arguments.size() < 1)
                    throw new Error("Invalid invocation to 'grade-down'.");
                return new Atom(new LbcSupplier<>(() -> {
                    if(arguments.size() == 1 && arguments.get(0).getType() == Type.LIST) {
                        List<Atom> l = arguments.get(0).getList().get();
                        return IntStream.range(0, l.size()).boxed().sorted(new Comparator<Integer>() {
                            @Override
                            public int compare(Integer o1, Integer o2) {
                                return new AtomComparator().compare(l.get(o2), l.get(o1));
                            }
                        }).map(x -> new Atom(BigDecimal.valueOf(x))).collect(Collectors.toList());
                    } else if(arguments.size() == 2 && arguments.get(0).isCallable() && arguments.get(1).getType() == Type.LIST) {
                        Callable c = arguments.get(0).getCallable().get();
                        List<Atom> l = arguments.get(1).getList().get();
                        return IntStream.range(0, l.size()).boxed().sorted(new Comparator<Integer>() {
                            @Override
                            public int compare(Integer o1, Integer o2) {
                                Atom r = c.apply(env, List.of(l.get(o2), l.get(o1)));
                                r.guardType("'grade-down' comparator", Type.NUMBER);
                                return r.getNumber().get().intValue();
                            }
                        }).map(x -> new Atom(BigDecimal.valueOf(x))).collect(Collectors.toList());
                    } else
                        throw new Error("'grade-down' expects (closure, list) arguments or a list");
                }));
            }
        }));

        env.push("index", new Atom(new Closure() {
            @Override
            public Atom apply(Executor env, List<Atom> arguments) {
                if(arguments.size() != 2)
                    throw new Error("Invalid invocation to 'index'.");
                return new Atom(new LbcSupplier<>(() -> {
                    Atom a = arguments.get(0);
                    Atom b = arguments.get(1);
                    a.guardType("First argument to 'index'", Type.LIST);
                    b.guardType("Second argument to 'index'", Type.LIST, Type.STRING_CONSTANT);
                    List<Atom> l = a.getList().get();
                    l.forEach(x -> x.guardType("First 'index' list", Type.NUMBER));
                    if(b.getType() == Type.LIST) {
                        return l.stream().map(x -> b.getList().get().get(x.getNumber().get().intValue())).collect(Collectors.toList());
                    } else {
                        return l.stream().map(x -> String.valueOf(b.getStringConstant().get().get().charAt(x.getNumber().get().intValue()))).collect(Collectors.toList());
                    }
                }));
            }
        }));

        env.push("at", new Atom(new Closure() {
            @Override
            public Atom apply(Executor env, List<Atom> arguments) {
                if(arguments.size() != 3)
                    throw new Error("Invalid invocation to 'at'.");
                return new Atom(new LbcSupplier<>(() -> {
                    arguments.get(0).guardType("First argument to 'at'", Type.CLOSURE, Type.MACRO);
                    arguments.get(1).guardType("Second argument to 'at'", Type.LIST, Type.CLOSURE, Type.MACRO);
                    arguments.get(2).guardType("Third argument to 'at'", Type.LIST);
                    Callable proc = arguments.get(0).getCallable().get();

                    if(arguments.get(1).isCallable()) {
                        Callable cond = arguments.get(1).getCallable().get();
                        List<Atom> l = arguments.get(2).getList().get();
                        List<Atom> rightSpots = l.stream().map(x -> cond.apply(env, List.of(x))).collect(Collectors.toList());
                        return Streams.zip(l.stream(), rightSpots.stream(), (x, y) -> {
                            if(y.coerceBool())
                                return proc.apply(env, List.of(x));
                            else
                                return x;
                        }).collect(Collectors.toList());
                    } else if(arguments.get(1).getType() == Type.LIST) {
                        List<Integer> l = arguments.get(1).getList().get().stream().map(x -> {
                            x.guardType("Second 'at' list", Type.NUMBER);
                            return x.getNumber().get().intValue();
                        }).collect(Collectors.toList());
                        List<Atom> r = new LinkedList<>(arguments.get(2).getList().get());
                        l.forEach(x -> r.set(x, proc.apply(env, List.of(r.get(x)))));
                        return r;
                    }

                    throw new Error("Unreachable");
                }));
            }
        }));

        env.push("unique", new Atom(new Closure() {
            @Override
            public Atom apply(Executor env, List<Atom> arguments) {
                if(arguments.size() != 1)
                    throw new Error("Invalid invocation to 'unique'.");
                return new Atom(new LbcSupplier<>(() -> {
                    if(arguments.get(0).getType() == Type.LIST) {
                        // XXX: Stream.distinct() is broken.
                        List<Atom> l = new LinkedList<>(arguments.get(0).getList().get());
                        for(int i = 0; i < l.size(); i++)
                            for(int j = i + 1; j < l.size(); j++)
                                if(l.get(i).equals(l.get(j)))
                                    l.remove(j--);
                        return l;
                    } else if(arguments.get(0).getType() == Type.STRING_CONSTANT) {
                        return arguments
                                .get(0)
                                .getStringConstant()
                                .get().get()
                                .codePoints()
                                .distinct()
                                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                                .toString();
                    }

                    throw new Error("'unique' expects a list or string as it's argument.");
                }));
            }
        }));

        env.push("where", new Atom(new Closure() {
            @Override
            public Atom apply(Executor env, List<Atom> arguments) {
                if(arguments.size() != 1)
                    throw new Error("Invalid invocation to 'where'.");
                return new Atom(new LbcSupplier<>(() -> {
                    arguments.get(0).guardType("First argument to 'where'", Type.LIST);
                    return Streams.zip(
                            arguments
                                    .get(0)
                                    .getList()
                                    .get()
                                    .stream()
                                    .map(x -> {
                                        x.guardType("'where' argument list contents", Type.NUMBER);
                                        return x.getNumber().get().intValue();
                                    }),
                            IntStream.range(0,
                                    arguments
                                            .get(0)
                                            .getList()
                                            .get()
                                            .size()).mapToObj(x -> new Atom(BigDecimal.valueOf(x))),
                            (x, y) -> Collections.nCopies(x, y)).flatMap(Collection::stream).collect(Collectors.toList());
                }));
            }
        }));

        env.push("replicate", new Atom(new Closure() {
            @Override
            public Atom apply(Executor env, List<Atom> arguments) {
                if(arguments.size() != 2)
                    throw new Error("Invalid invocation to 'replicate'.");
                return new Atom(new LbcSupplier<>(() -> {
                    if(arguments.get(0).getType() == Type.NUMBER) {
                        if (arguments.get(1).getType() != Type.LIST) {
                            return Collections.nCopies(arguments.get(0).getNumber().get().intValue(), arguments.get(1));
                        } else {
                            return arguments.get(1).getList().get().stream().map(x -> {
                                return Collections.nCopies(arguments.get(0).getNumber().get().intValue(), x);
                            }).flatMap(Collection::stream).collect(Collectors.toList());
                        }
                    } else if(arguments.get(0).getType() == Type.LIST) {
                        List<Integer> l1 = arguments.get(0).getList().get().stream().map(x -> {
                            x.guardType("'replicate' argument list contents", Type.NUMBER);
                            return x.getNumber().get().intValue();
                        }).collect(Collectors.toList());
                        arguments.get(1).guardType("'replicate' argument", Type.LIST);
                        List<Atom> l2 = arguments.get(1).getList().get();
                        if(l1.size() != l2.size())
                            throw new Error("'replicate' expects two lists of the same size.");
                        return Streams.zip(l1.stream(), l2.stream(), (x, y) -> Collections.nCopies(x, y)).flatMap(Collection::stream).collect(Collectors.toList());
                    } else {
                        throw new Error("'replicate' expects a number or list as it's first argument.");
                    }
                }));
            }
        }));

        env.push("drop", new Atom(new Closure() {
            @Override
            public Atom apply(Executor env, List<Atom> arguments) {
                if(arguments.size() != 2)
                    throw new Error("Invalid invocation to 'drop'.");
                return new Atom(new LbcSupplier<>(() -> {
                    arguments.get(0).guardType("First argument to 'drop'", Type.NUMBER);
                    arguments.get(1).guardType("Second argument to 'drop'", Type.LIST);
                    int n = arguments.get(0).getNumber().get().intValue();
                    if(n > arguments.get(1).getList().get().size())
                        throw new Error("'drop' argument 1 is greater than the size of argument 2.");
                    if(n == 0)
                        return arguments.get(1);
                    else if(n > 0)
                        return arguments.get(1).getList().get().stream().skip(n).collect(Collectors.toList());
                    else
                        return Lists.reverse(Lists.reverse(arguments.get(1).getList().get()).stream().skip(-n).collect(Collectors.toList()));
                }));
            }
        }));

        env.push("intersperse", new Atom(new Closure() {
            @Override
            public Atom apply(Executor env, List<Atom> arguments) {
                if(arguments.size() != 2)
                    throw new Error("Invalid invocation to 'intersperse'.");
                return new Atom(new LbcSupplier<>(() -> {
                    arguments.get(1).guardType("Second argument to 'intersperse'", Type.LIST);
                    List<Atom> l2 = arguments.get(1).getList().get();
                    if(arguments.get(0).getType() == Type.LIST) {
                        List<Atom> l1 = arguments.get(0).getList().get();
                        if (l1.size() != l2.size())
                            throw new Error("'intersperse' expects two lists of the same size.");
                        return Streams.zip(l1.stream(), l2.stream(), (x, y) -> Lists.newArrayList(x, y)).flatMap(Collection::stream).collect(Collectors.toList());
                    } else {
                        return l2.stream().map(x -> List.of(arguments.get(0), x)).flatMap(Collection::stream).skip(1).collect(Collectors.toList());
                    }
                }));
            }
        }));

        env.push("unique-mask", new Atom(new Closure() {
            @Override
            public Atom apply(Executor env, List<Atom> arguments) {
                if(arguments.size() != 1)
                    throw new Error("Invalid invocation to 'unique-mask'.");
                return new Atom(new LbcSupplier<>(() -> {
                    Atom a = arguments.get(0);
                    if(a.getType() == Type.LIST) {
                        // XXX: See `unique` implementation.
                        List<Atom> l = new LinkedList<>(a.getList().get());
                        for(int i = 0; i < l.size(); i++)
                            for(int j = i + 1; j < l.size(); j++)
                                if(l.get(i).equals(l.get(j)))
                                    l.remove(j--);
                        return a.getList().get().stream().map(x -> {
                            if(l.contains(x)) {
                                l.remove(x);
                                return new Atom(BigDecimal.ONE);
                            } else {
                                return new Atom(BigDecimal.ZERO);
                            }
                        }).collect(Collectors.toList());
                    } else if(a.getType() == Type.STRING_CONSTANT) {
                        List<Integer> l = a.getStringConstant().get().get()
                                .codePoints().distinct().boxed().collect(Collectors.toList());
                        return a.getStringConstant().get().get().codePoints().mapToObj(x -> {
                            if(l.contains(x)) {
                                l.remove(x);
                                return new Atom(BigDecimal.ONE);
                            } else {
                                return new Atom(BigDecimal.ZERO);
                            }
                        }).collect(Collectors.toList());
                    } else
                        throw new Error("'unique-mask' expects a list or a string.");
                }));
            }
        }));

        env.push("prefixes", new Atom(new Closure() {
            @Override
            public Atom apply(Executor env, List<Atom> arguments) {
                if(arguments.size() != 1)
                    throw new Error("Invalid invocation to 'prefixes'.");
                return new Atom(new LbcSupplier<>(() -> {
                    if(arguments.get(0).getType() == Type.LIST) {
                        List<Atom> l = arguments.get(0).getList().get();
                        return IntStream.range(0, l.size()).mapToObj(i -> new Atom(l.subList(0, i + 1))).collect(Collectors.toList());
                    } else if(arguments.get(0).getType() == Type.STRING_CONSTANT) {
                        List<Integer> l = arguments.get(0).getStringConstant().get().get().codePoints().boxed().collect(Collectors.toList());
                        return IntStream.range(0, l.size())
                                .mapToObj(i ->
                                        new Atom(new StringConstant(
                                                l.subList(0, i + 1).stream()
                                                        .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                                                        .toString())))
                                .collect(Collectors.toList());
                    } else {
                        throw new Error("'prefixes' expects a list or a string.");
                    }
                }));
            }
        }));

        env.push("suffixes", new Atom(new Closure() {
            @Override
            public Atom apply(Executor env, List<Atom> arguments) {
                if(arguments.size() != 1)
                    throw new Error("Invalid invocation to 'suffixes'.");
                return new Atom(new LbcSupplier<>(() -> {
                    if(arguments.get(0).getType() == Type.LIST) {
                        List<Atom> l = arguments.get(0).getList().get();
                        return Lists.reverse(IntStream.range(0, l.size()).mapToObj(i -> new Atom(l.subList(i, l.size()))).collect(Collectors.toList()));
                    } else if(arguments.get(0).getType() == Type.STRING_CONSTANT) {
                        List<Integer> l = arguments.get(0).getStringConstant().get().get().codePoints().boxed().collect(Collectors.toList());
                        return Lists.reverse(IntStream.range(0, l.size())
                                .mapToObj(i ->
                                        new Atom(new StringConstant(
                                                l.subList(i, l.size()).stream()
                                                        .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                                                        .toString())))
                                .collect(Collectors.toList()));
                    } else {
                        throw new Error("'suffixes' expects a list or a string.");
                    }
                }));
            }
        }));

        env.push("partition", new Atom(new Closure() {
            @Override
            public Atom apply(Executor env, List<Atom> arguments) {
                if(arguments.size() != 2)
                    throw new Error("Invalid invocation to 'partition'.");
                return new Atom(new LbcSupplier<>(() -> {
                    arguments.get(0).guardType("First argument to 'partition'.", Type.LIST);
                    arguments.get(1).guardType("Second argument to 'partition'.", Type.LIST);
                    List<Boolean> l1 = Stream.concat(arguments.get(0).getList().get().stream().map(x -> x.coerceBool()), Stream.of(Boolean.TRUE))
                            .collect(Collectors.toList());
                    List<Atom> l2 = arguments.get(1).getList().get();
                    // Java needs explicit generic specification because the compiler is extremely dumb.
                    // Equivalent APL code:
                    // ⍸
                    List<Integer> where = Streams.<Integer, Boolean, List<Integer>>zip(IntStream.range(0, l1.size()).boxed(), l1.stream(), (i, b) -> {
                        if(b)
                            return List.of(i);
                        else
                            return List.of();
                    }).flatMap(Collection::stream).collect(Collectors.toList());
                    // Equivalent APL code:
                    // |2-/
                    Stream<Integer> differences = IntStream.range(0, where.size() - 1).mapToObj(i -> Math.abs(where.get(i + 1) - where.get(i)));
                    // Java to APL code volume ratio: 1206 / 5 = 240
                    return Streams.zip(where.stream(), differences, (i, d) -> new Atom(l2.subList(i, i + d))).collect(Collectors.toList());
                }));
            }
        }));

        env.push("window", new Atom(new Closure() {
            @Override
            public Atom apply(Executor env, List<Atom> arguments) {
                if(arguments.size() != 2)
                    throw new Error("Invalid invocation to 'window'.");
                return new Atom(new LbcSupplier<>(() -> {
                    arguments.get(0).guardType("First argument to 'window'.", Type.NUMBER);
                    int windowSize = arguments.get(0).getNumber().get().intValue();
                    if(arguments.get(1).getType() == Type.LIST) {
                        List<Atom> data = arguments.get(1).getList().get();
                        return IntStream.range(0, data.size() - windowSize + 1).mapToObj(i -> new Atom(data.subList(i, i + windowSize))).collect(Collectors.toList());
                    } else if(arguments.get(1).getType() == Type.STRING_CONSTANT) {
                        String data = arguments.get(1).getStringConstant().get().get();
                        return IntStream.range(0, data.length() - windowSize + 1).mapToObj(i -> new Atom(new StringConstant(data.substring(i, i + windowSize)))).collect(Collectors.toList());
                    } else
                        throw new Error("Invalid invocation to 'window'. The second argument was expected to be either a list or a string constant.");
                }));
            }
        }));

        env.push("inner-prod", new Atom(new Closure() {
            @Override
            public Atom apply(Executor env, List<Atom> arguments) {
                if(arguments.size() != 4)
                    throw new Error("Invalid invocation to 'inner-prod'.");
                return new Atom(new LbcSupplier<>(() -> {
                    arguments.get(0).guardType("First argument to 'inner-prod'.", Type.CLOSURE, Type.MACRO);
                    arguments.get(1).guardType("Second argument to 'inner-prod'.", Type.CLOSURE, Type.MACRO);
                    arguments.get(2).guardType("Third argument to 'inner-prod'.", Type.LIST, Type.MATRIX);
                    arguments.get(3).guardType("Fourth argument to 'inner-prod'.", Type.LIST, Type.MATRIX);
                    if(arguments.get(2).getType() != arguments.get(3).getType())
                        throw new Error("Invalid invocation to 'inner-prod'. Expected two matrices or lists.");
                    if(arguments.get(2).getType() == Type.LIST) {
                        List<Atom> l1 = arguments.get(2).getList().get();
                        List<Atom> l2 = arguments.get(3).getList().get();
                        Callable f = arguments.get(0).getCallable().get();
                        Callable g = arguments.get(1).getCallable().get();
                        if (l1.size() != l2.size())
                            throw new Error("The length of lists provided to 'inner-prod' doesn't match.");
                        else if (l1.size() == 0)
                            return Atom.NULL.get().get();
                        else if (l1.size() == 1)
                            return g.apply(env, List.of(l1.get(0), l2.get(0))).get().get();
                        else
                            return Streams.zip(l1.stream(), l2.stream(), (x, y) -> g.apply(env, List.of(x, y)))
                                    .reduce((x, y) -> f.apply(env, List.of(x, y))).get().get().get();
                    } else {
                        Matrix a = arguments.get(2).getMatrix().get();
                        Matrix b = arguments.get(3).getMatrix().get();
                        Callable f = arguments.get(0).getCallable().get();
                        Callable g = arguments.get(1).getCallable().get();
                        if(a.getRows() != b.getCols())
                            throw new Error("Invalid matrix inner product: " + a.getRows() + "x" + a.getCols() + " and " + b.getRows() + "x" + b.getCols() + ".");
                        List<List<Atom>> lRows = a.rows().collect(Collectors.toList());
                        List<List<Atom>> lCols = b.cols().collect(Collectors.toList());
                        return Matrix.of((row, col) ->
                                Streams.zip(lRows.get(row).stream(), lCols.get(col).stream(), (x, y) -> g.apply(env, List.of(x, y)))
                                        .reduce((x, y) -> f.apply(env, List.of(x, y))).get(), a.getRows(), b.getCols());
                    }
                }));
            }
        }));

        env.push("outer-prod", new Atom(new Closure() {
            @Override
            public Atom apply(Executor env, List<Atom> arguments) {
                if(arguments.size() != 3)
                    throw new Error("Invalid invocation to 'outer-prod'.");
                return new Atom(new LbcSupplier<>(() -> {
                    arguments.get(0).guardType("First argument to 'outer-prod'", Type.CLOSURE, Type.MACRO);
                    arguments.get(1).guardType("Second argument to 'outer-prod'", Type.LIST);
                    arguments.get(2).guardType("Third argument to 'outer-prod'", Type.LIST);
                    List<Atom> l1 = arguments.get(1).getList().get();
                    List<Atom> l2 = arguments.get(2).getList().get();
                    Callable f = arguments.get(0).getCallable().get();
                    return cartesianProduct(List.of(l1, l2)).stream().map(x -> f.apply(env, x)).collect(Collectors.toList());
                }));
            }
        }));

        env.push("range", new Atom(new Closure() {
            @Override
            public Atom apply(Executor env, List<Atom> arguments) {
                if(arguments.size() != 2)
                    throw new Error("Invalid invocation to 'range'.");
                return new Atom(new LbcSupplier<>(() -> {
                    arguments.get(0).guardType("First argument to 'range'", Type.NUMBER);
                    arguments.get(1).guardType("First argument to 'range'", Type.NUMBER);
                    BigDecimal start = arguments.get(0).getNumber().get();
                    BigDecimal end = arguments.get(1).getNumber().get();
                    return Stream.iterate(start, x -> x.compareTo(end) < 0, x -> x.add(BigDecimal.ONE)).map(Atom::new).collect(Collectors.toList());
                }));
            }
        }));

        env.push("starts-with", new Atom(new Closure() {
            @Override
            public Atom apply(Executor env, List<Atom> arguments) {
                if(arguments.size() != 2)
                    throw new Error("Invalid invocation to 'starts-with'.");
                return new Atom(new LbcSupplier<>(() -> {
                    Atom a1, a2;
                    a1 = arguments.get(0);
                    a2 = arguments.get(1);
                    if(a1.getType() == Type.LIST && a2.getType() == Type.LIST) {
                        List<Atom> l1 = a1.getList().get();
                        List<Atom> l2 = a2.getList().get();
                        return new BigDecimal(Streams.zip(l1.stream(), l2.stream(), (x, y) -> x.equals(y)).anyMatch(x -> !x) ? 0 : 1);
                    } else if(a1.getType() == Type.STRING_CONSTANT && a2.getType() == Type.STRING_CONSTANT) {
                        String s1 = a1.getStringConstant().get().get();
                        String s2 = a2.getStringConstant().get().get();
                        return s2.startsWith(s1) ? BigDecimal.ZERO : BigDecimal.ONE;
                    } else {
                        throw new Error("Invalid invocation to 'starts-with'. Expected two strings or two lists, got " + a1.getType() + " and " + a2.getType() + ".");
                    }
                }));
            }
        }));

        env.push("keys", new Atom(new Closure() {
            @Override
            public Atom apply(Executor env, List<Atom> arguments) {
                if(arguments.size() != 1)
                    throw new Error("Invalid invocation to 'keys'.");
                return new Atom(new LbcSupplier<>(() -> {
                    if(arguments.get(0).getType() == Type.LIST) {
                        // XXX: See implementation notes for 'unique'.
                        List<Atom> l = new LinkedList<>(arguments.get(0).getList().get());
                        for(int i = 0; i < l.size(); i++)
                            for(int j = i + 1; j < l.size(); j++)
                                if(l.get(i).equals(l.get(j)))
                                    l.remove(j--);
                        List<Atom> atoms = new LinkedList<>();
                        List<List<Atom>> values = new LinkedList<>();
                        for(Atom a : l) {
                            atoms.add(a);
                            values.add(new LinkedList<>());
                        }
                        AtomicInteger position = new AtomicInteger(0);
                        arguments.get(0).getList().get().forEach(x -> {
                            for(int i = 0; i < atoms.size(); i++) {
                                if(atoms.get(i).equals(x)) {
                                    values.get(i).add(new Atom(new BigDecimal(position.get())));
                                    position.incrementAndGet();
                                    break;
                                }
                            }
                        });
                        return Streams.zip(atoms.stream(), values.stream(), (x, y) -> new Atom(List.of(x, new Atom(y)))).collect(Collectors.toList());
                    } else if(arguments.get(0).getType() == Type.STRING_CONSTANT) {
                        String s = arguments.get(0).getStringConstant().get().get();
                        IntStream uniques = s.codePoints().distinct();
                        LinkedHashMap<Integer, List<Atom>> o = new LinkedHashMap<>();
                        uniques.forEach(x -> o.put(x, new LinkedList<>()));
                        AtomicInteger position = new AtomicInteger();
                        s.codePoints().forEach(x -> {
                            o.get(x).add(new Atom(new BigDecimal(position.get())));
                            position.getAndIncrement();
                        });
                        return new LinkedList<>(o.entrySet()).stream()
                                .map(x -> new Atom(List.of(
                                        new Atom(new StringConstant(new StringBuilder().appendCodePoint(x.getKey()).toString())),
                                        new Atom(x.getValue())))).collect(Collectors.toList());
                    }

                    throw new Error("'keys' expects a list or string as it's argument.");
                }));
            }
        }));

        env.push("index-of", new Atom(new Closure() {
            @Override
            public Atom apply(Executor env, List<Atom> arguments) {
                if(arguments.size() != 2)
                    throw new Error("Invalid invocation to 'index-of'.");
                return new Atom(new LbcSupplier<>(() -> {
                    if(arguments.get(0).getType() == Type.LIST && arguments.get(1).getType() == Type.LIST) {
                        List<Atom> l1 = arguments.get(0).getList().get();
                        List<Atom> l2 = arguments.get(1).getList().get();
                        return l1.stream().map(x -> new Atom(new BigDecimal(l2.indexOf(x)))).collect(Collectors.toList());
                    } else if(arguments.get(0).getType() == Type.STRING_CONSTANT && arguments.get(1).getType() == Type.STRING_CONSTANT) {
                        String l1 = arguments.get(0).getStringConstant().get().get();
                        String l2 = arguments.get(1).getStringConstant().get().get();
                        LinkedList<Atom> l = new LinkedList<>();
                        for(char c : l1.toCharArray())
                            l.add(new Atom(new BigDecimal(l2.indexOf(c))));
                        return l;
                    } else
                        throw new Error("'index-of' expects two lists and strings as its arguments.");
                }));
            }
        }));

        env.push("ucs", new Atom(new Closure() {
            @Override
            public Atom apply(Executor env, List<Atom> arguments) {
                if(arguments.size() != 1)
                    throw new Error("Invalid invocation to 'ucs'.");
                return new Atom(new LbcSupplier<>(() -> {
                    if(arguments.get(0).getType() == Type.STRING_CONSTANT) {
                        String s = arguments.get(0).getStringConstant().get().get();
                        return s.codePoints().mapToObj(x -> new Atom(new BigDecimal(x))).collect(Collectors.toList());
                    } else if(arguments.get(0).getType() == Type.LIST) {
                        List<Atom> l = arguments.get(0).getList().get();
                        return new StringConstant(l.stream().mapToInt(x -> {
                            x.guardType("List argument to 'ucs'", Type.NUMBER);
                            return x.getNumber().get().intValue();
                        }).collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString());
                    } else
                        throw new Error("'ucs' expects a list or string as its argument.");
                }));
            }
        }));

        env.push("in?", new Atom(new Closure() {
            @Override
            public Atom apply(Executor env, List<Atom> arguments) {
                if(arguments.size() != 2)
                    throw new Error("Invalid invocation to 'in?'.");
                return new Atom(new LbcSupplier<>(() -> {
                    if(arguments.get(0).getType() == Type.STRING_CONSTANT && arguments.get(1).getType() == Type.STRING_CONSTANT) {
                        String l1 = arguments.get(0).getStringConstant().get().get();
                        String l2 = arguments.get(1).getStringConstant().get().get();
                        LinkedList<Atom> l = new LinkedList<>();
                        for(char c : l1.toCharArray())
                            l.add(new Atom(new BigDecimal(l2.indexOf(c) != -1 ? 1 : 0)));
                        return l;
                    } else if(arguments.get(1).getType() == Type.LIST) {
                        List<Atom> l = arguments.get(1).getList().get();
                        return new BigDecimal(l.contains(arguments.get(0)) ? 1 : 0);
                    } else
                        throw new Error("'in?' expects two lists and strings as its arguments.");
                }));
            }
        }));

        env.push("find-seq", new Atom(new Closure() {
            @Override
            public Atom apply(Executor env, List<Atom> arguments) {
                if(arguments.size() != 2)
                    throw new Error("Invalid invocation to 'find-seq'.");
                return new Atom(new LbcSupplier<>(() -> {
                    if(arguments.get(0).getType() == Type.STRING_CONSTANT && arguments.get(1).getType() == Type.STRING_CONSTANT) {
                        String needle = arguments.get(0).getStringConstant().get().get();
                        String haystack = arguments.get(1).getStringConstant().get().get();
                        int windowSize = needle.length();
                        Stream<String> s = IntStream.range(0, haystack.length() - windowSize + 1).mapToObj(i -> haystack.substring(i, i + windowSize));
                        return Stream.concat(s.map(x -> x.equals(needle) ? BigDecimal.ONE : BigDecimal.ZERO), Collections.nCopies(windowSize - 1, BigDecimal.ZERO).stream())
                                .map(Atom::new).collect(Collectors.toList());
                    } else if(arguments.get(0).getType() == Type.LIST && arguments.get(1).getType() == Type.LIST) {
                        List<Atom> needle = arguments.get(0).getList().get();
                        List<Atom> haystack = arguments.get(1).getList().get();
                        int windowSize = needle.size();
                        Stream<List<Atom>> s = IntStream.range(0, haystack.size() - windowSize + 1).mapToObj(i -> haystack.subList(i, i + windowSize));
                        return Stream.concat(s.map(x -> x.equals(needle) ? BigDecimal.ONE : BigDecimal.ZERO), Collections.nCopies(windowSize - 1, BigDecimal.ZERO).stream())
                                .map(Atom::new).collect(Collectors.toList());
                    } else
                        throw new Error("'find-seq' expects two lists and strings as its arguments.");
                }));
            }
        }));

        env.push("shuffle", new Atom(new Closure() {
            @Override
            public Atom apply(Executor env, List<Atom> arguments) {
                if(arguments.size() != 1)
                    throw new Error("Invalid invocation to 'shuffle'.");
                return new Atom(new LbcSupplier<>(() -> {
                    if(arguments.get(0).getType() == Type.LIST) {
                        List<Atom> l = arguments.get(0).getList().get();
                        Collections.shuffle(l);
                        return l;
                    } else
                        throw new Error("'shuffle' expects a list as its argument.");
                }));
            }
        }));

        Evaluation.evalString(env, "(defun str-explode (x) (str-split x \"\"))");
        Evaluation.evalString(env, "(defun str-join (x) (foldl + \"\" x))");
        Evaluation.evalString(env, "(defun cadr (x) (car (cdr x)))");
    }
}
