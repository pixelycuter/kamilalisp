package kamilalisp.libs.primitives.math;

import ch.obermuhlner.math.big.BigComplex;
import kamilalisp.data.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

public class Derivative implements Closure {
    public Atom D(Atom f, String v) {
        if(f.getType() == Type.CLOSURE)
            return D(f.getClosure().get().requote().get(1), v);
        else if(f.getType() == Type.COMPLEX || f.getType() == Type.NUMBER)
            return new Atom(BigDecimal.ZERO);
        else if(f.getType() == Type.STRING)
            if(f.getString().get().equals(v))
                return new Atom(BigDecimal.ONE);
            else
                return new Atom(BigDecimal.ZERO); // Partial derivative: treat variable as constant.
        else if(f.getType() != Type.LIST)
            throw new Error("'D': Derivative of " + f.getType() + " is not supported: " + f.get().get());

        List<Atom> expr = f.getList().get();
        if(expr.isEmpty())
            throw new Error("'D': Derivative of an empty list is not supported.");
        expr.get(0).guardType("'D' function head", Type.STRING);
        switch(expr.get(0).getString().get()) {
            case "+":
                // d/dx conj(x) = undefined
                // d/dx a + b = d/dx a + d/dx b
                if(expr.size() == 2)
                    throw new Error("d/dx conj(x) impossible.");
                else if(expr.size() == 3)
                    return new Atom(List.of(new Atom("+"), D(expr.get(1), v), D(expr.get(2), v)));
                else
                    throw new Error("Invalid invocation to +.");
            case "-":
                if(expr.size() == 2)
                    return new Atom(List.of(new Atom("-"), D(expr.get(1), v)));
                else if(expr.size() == 3)
                    return new Atom(List.of(new Atom("-"), D(expr.get(1), v), D(expr.get(2), v)));
                else
                    throw new Error("Invalid invocation to -.");
            case "*":
                // d/dx a * b = d/dx a * b + d/dx b * a
                if(expr.size() == 2)
                    throw new Error("d/dx signum(x) = 2δ(x), although dirac delta is a lie.");
                else if(expr.size() == 3)
                    return new Atom(List.of(new Atom("+"),
                            new Atom(List.of(new Atom("*"), D(expr.get(1), v), expr.get(2))),
                            new Atom(List.of(new Atom("*"), D(expr.get(2), v), expr.get(1)))));
                else
                    throw new Error("Invalid invocation to *.");
            case "/":
                // d/dx 1 / f(x) = - d/dx f(x) / f(x)^2
                // d/dx f(x) / g(x) = ((d/dx f(x)) * g(x) - (d/dx g(x)) * f(x)) / g(x)^2
                if(expr.size() == 2)
                    return new Atom(List.of(new Atom("-"),
                            new Atom(List.of(new Atom("/"), D(expr.get(1), v),
                                    new Atom(List.of(new Atom("**"), expr.get(1), new Atom(new BigDecimal(2))))))));
                else if(expr.size() == 3)
                    return new Atom(List.of(new Atom("/"), new Atom(List.of(new Atom("-"),
                                    new Atom(List.of(new Atom("*"), D(expr.get(1), v), expr.get(2))),
                                    new Atom(List.of(new Atom("*"), D(expr.get(2), v), expr.get(1))))),
                            new Atom(List.of(new Atom("**"), expr.get(2), new Atom(new BigDecimal(2))))));
                else
                    throw new Error("Invalid invocation to /.");
            case "**":
                // Better get your fucking seatbelts ready, punk.
                // d/dx (f(x) ** g(x)) = f(x) ** (g(x) - 1) * (g(x) * (d/dx f(x)) + f(x) * log(f(x)) * (d/dx g(x)))
                if(expr.size() != 3)
                    throw new Error("Invalid invocation to **.");
                // d/dx (f(x) ** g(x)) = a * (c + d)
                // a = f(x) ** (g(x) - 1)
                // c = g(x) * (d/dx f(x))
                // d = f(x) * log(f(x)) * (d/dx g(x))
                Atom a = new Atom(List.of(new Atom("**"), expr.get(1), new Atom(List.of(new Atom("-"), expr.get(2), new Atom(new BigDecimal(1))))));
                Atom c = new Atom(List.of(new Atom("*"), expr.get(2), D(expr.get(1), v)));
                Atom d = new Atom(List.of(new Atom("*"), new Atom(List.of(new Atom("*"), expr.get(1), D(expr.get(2), v))), new Atom(List.of(new Atom("ln"), expr.get(1)))));
                return new Atom(List.of(new Atom("*"), a, new Atom(List.of(new Atom("+"), c, d))));
            case "sin":
                if(expr.size() != 2)
                    throw new Error("Invalid invocation to sin.");
                // d/dx sin(f(x)) = cos(f(x)) * d/dx f(x)
                return new Atom(List.of(new Atom("*"),
                        new Atom(List.of(new Atom("cos"), expr.get(1))),
                        D(expr.get(1), v)));
            case "cos":
                if(expr.size() != 2)
                    throw new Error("Invalid invocation to cos.");
                // d/dx cos(f(x)) = - sin(f(x)) * d/dx f(x)
                return new Atom(List.of(new Atom("*"),
                        new Atom(List.of(new Atom("-"), new Atom(List.of(new Atom("sin"), expr.get(1))))),
                        D(expr.get(1), v)));
            case "sqrt":
                if(expr.size() != 2)
                    throw new Error("Invalid invocation to sqrt.");
                // d/dx sqrt(f(x)) = (d/dx f(x)) / (2 * sqrt(f(x)))
                return new Atom(List.of(new Atom("/"),
                        D(expr.get(1), v),
                        new Atom(List.of(new Atom("*"), new Atom(new BigDecimal(2)), new Atom(List.of(new Atom("sqrt"), expr.get(1)))))));
            case "ln":
                if(expr.size() != 2)
                    throw new Error("Invalid invocation to ln.");
                // d/dx ln(f(x)) = (d/dx f(x)) / f(x)
                return new Atom(List.of(new Atom("/"),
                        D(expr.get(1), v),
                        expr.get(1)));
            case "exp":
                if(expr.size() != 2)
                    throw new Error("Invalid invocation to exp.");
                // d/dx exp(f(x)) = exp(f(x)) * d/dx f(x)
                return new Atom(List.of(new Atom("*"),
                        new Atom(List.of(new Atom("exp"), expr.get(1))),
                        D(expr.get(1), v)));
            case "tan":
                if(expr.size() != 2)
                    throw new Error("Invalid invocation to tan.");
                // d/dx tan(f(x)) = (d/dx f(x)) * sec^2(f(x))
                return new Atom(List.of(new Atom("*"),
                        D(expr.get(1), v),
                        new Atom(List.of(new Atom("**"), new Atom(List.of(new Atom("sec"), expr.get(1))), new Atom(new BigDecimal(2))))));
            case "ctan":
                if(expr.size() != 2)
                    throw new Error("Invalid invocation to ctan.");
                // d/dx ctan(f(x)) = (d/dx f(x)) * (- (csec^2(f(x))))
                return new Atom(List.of(new Atom("*"),
                        D(expr.get(1), v),
                        new Atom(List.of(new Atom("*"), new Atom(new BigDecimal(-1)), new Atom(List.of(new Atom("**"), new Atom(List.of(new Atom("csec"), expr.get(1))), new Atom(new BigDecimal(2))))))));
            case "sec":
                if(expr.size() != 2)
                    throw new Error("Invalid invocation to sec.");
                // d/dx sec(f(x)) = (d/dx f(x)) * (sec(f(x)) * tan(f(x)))
                return new Atom(List.of(new Atom("*"),
                        D(expr.get(1), v),
                        new Atom(List.of(new Atom("*"), new Atom(List.of(new Atom("sec"), expr.get(1))), new Atom(List.of(new Atom("tan"), expr.get(1)))))));
            case "csec":
                if(expr.size() != 2)
                    throw new Error("Invalid invocation to csec.");
                // d/dx csec(f(x)) = (d/dx f(x)) * (- (csec(f(x)) * ctan(f(x))))
                return new Atom(List.of(new Atom("*"),
                        D(expr.get(1), v),
                        new Atom(List.of(new Atom("*"), new Atom(new BigDecimal(-1)),
                                new Atom(List.of(new Atom("*"), new Atom(List.of(new Atom("csec"), expr.get(1))), new Atom(List.of(new Atom("ctan"), expr.get(1)))))))));
            case "lambert-w":
                if(expr.size() != 2)
                    throw new Error("Invalid invocation to lambert-w.");
                // d/dx W(f(x)) = (d/dx f(x)) * W(f(x)) / (f(x) * W(f(x)) + f(x))
                // d/dx W(f(x)) = a/b
                // a = W(f(x)) * d/dx f(x)
                // b = (f(x) * W(f(x))) + f(x) = f(x) * (W(f(x)) + 1)
                Atom fA = new Atom(List.of(new Atom("*"),
                        new Atom(List.of(new Atom("lambert-w"), expr.get(1))),
                        D(expr.get(1), v)));
                Atom fB = new Atom(List.of(new Atom("*"),
                        expr.get(1),
                        new Atom(List.of(new Atom("+"), new Atom(List.of(new Atom("lambert-w"), expr.get(1))), new Atom(new BigDecimal(1))))));
                return new Atom(List.of(new Atom("/"), fA, fB));
        }

        throw new Error("'D': Can't compute the derivative of " + expr + ".");
    }

    public Atom evaluateSimpNumeric(Executor env, Atom f) {
        return env.evaluate(f);
    }

    public boolean isSimpConstant(Atom f) {
        return f.getType() != Type.STRING && f.getType() != Type.LIST && f.getType() != Type.CLOSURE;
    }

    public Atom trySimp(Executor env, List<Atom> f, int arity) {
        if(f.size() == arity && f.subList(1, f.size()).stream().allMatch(this::isSimpConstant))
            return evaluateSimpNumeric(env, new Atom(f));
        else
            return null;
    }

    public Atom simplify(Executor env, Atom f) {
        if(f.getType() != Type.LIST)
            return f;
        List<Atom> expr = f.getList().get();
        if(expr.isEmpty())
            throw new Error("'D': Simplification of an empty list is not supported.");
        expr.get(0).guardType("'D' Simplification submodule function head", Type.STRING);
        Atom result;
        switch(expr.get(0).getString().get()) {
            case "+":
                // Try to simplify conj(x) and x + y.
                // Also, try to cancel out 0+x and x+0.
                if((result = trySimp(env, expr, 2)) != null)
                    return result;
                else if((result = trySimp(env, expr, 3)) != null)
                    return result;
                else if(expr.size() == 3 && (
                        (expr.get(1).getType() == Type.NUMBER && expr.get(1).getNumber().get().compareTo(BigDecimal.ZERO) == 0)
                                || (expr.get(1).getType() == Type.COMPLEX && expr.get(1).getComplex().get().equals(BigComplex.ZERO))))
                    return expr.get(2);
                else if(expr.size() == 3 && (
                        (expr.get(2).getType() == Type.NUMBER && expr.get(2).getNumber().get().compareTo(BigDecimal.ZERO) == 0)
                                || (expr.get(2).getType() == Type.COMPLEX && expr.get(2).getComplex().get().equals(BigComplex.ZERO))))
                    return expr.get(1);
                break;
            case "-":
                // Try to simplify -x and x - y.
                // Also, try to simplify 0-x as -x and x-0 as x.
                if((result = trySimp(env, expr, 2)) != null)
                    return result;
                else if((result = trySimp(env, expr, 3)) != null)
                    return result;
                else if(expr.size() == 3 && (
                        (expr.get(1).getType() == Type.NUMBER && expr.get(1).getNumber().get().compareTo(BigDecimal.ONE) == 0)
                                || (expr.get(1).getType() == Type.COMPLEX && expr.get(1).getComplex().get().equals(BigComplex.ONE))))
                    return new Atom(List.of(new Atom("-"), expr.get(2)));
                else if(expr.size() == 3 && (
                        (expr.get(2).getType() == Type.NUMBER && expr.get(2).getNumber().get().compareTo(BigDecimal.ONE) == 0)
                                || (expr.get(2).getType() == Type.COMPLEX && expr.get(2).getComplex().get().equals(BigComplex.ONE))))
                    return expr.get(1);
                break;
            case "*":
                // Try to simplify signum(x) and x * y.
                // Also, try to simplify 0*x and x*0 to both 0, and 1*x and x*1 to both x.
                if((result = trySimp(env, expr, 2)) != null)
                    return result;
                else if((result = trySimp(env, expr, 3)) != null)
                    return result;
                else if(expr.size() == 3 && (
                        (expr.get(1).getType() == Type.NUMBER && expr.get(1).getNumber().get().compareTo(BigDecimal.ZERO) == 0)
                                || (expr.get(1).getType() == Type.COMPLEX && expr.get(1).getComplex().get().equals(BigComplex.ZERO))))
                    return new Atom(new BigDecimal(0));
                else if(expr.size() == 3 && (
                        (expr.get(2).getType() == Type.NUMBER && expr.get(2).getNumber().get().compareTo(BigDecimal.ZERO) == 0)
                                || (expr.get(2).getType() == Type.COMPLEX && expr.get(2).getComplex().get().equals(BigComplex.ZERO))))
                    return new Atom(new BigDecimal(0));
                else if(expr.size() == 3 && (
                        (expr.get(1).getType() == Type.NUMBER && expr.get(1).getNumber().get().compareTo(BigDecimal.ONE) == 0)
                                || (expr.get(1).getType() == Type.COMPLEX && expr.get(1).getComplex().get().equals(BigComplex.ONE))))
                    return expr.get(2);
                else if(expr.size() == 3 && (
                        (expr.get(2).getType() == Type.NUMBER && expr.get(2).getNumber().get().compareTo(BigDecimal.ONE) == 0)
                                || (expr.get(2).getType() == Type.COMPLEX && expr.get(2).getComplex().get().equals(BigComplex.ONE))))
                    return expr.get(1);
                break;
            case "**":
                // Simpify x ** 1 to x.
                if(expr.size() == 3 && (
                        (expr.get(2).getType() == Type.NUMBER && expr.get(2).getNumber().get().compareTo(BigDecimal.ONE) == 0)
                                || (expr.get(2).getType() == Type.COMPLEX && expr.get(2).getComplex().get().equals(BigComplex.ONE))))
                    return expr.get(1);
                break;
        }
        return new Atom(expr.stream().map(x -> simplify(env, x)).collect(Collectors.toList()));
    }

    public Atom maxSimplify(Executor env, Atom a) {
        Atom deriv = a;
        Atom prev = null;
        while(!deriv.equals(prev)) {
            prev = deriv;
            deriv = simplify(env, deriv);
        }
        return deriv;
    }

    public Atom deriv(Executor env, Closure c, String v) {
        Atom code = c.requote().get(1);
        Atom params = c.requote().get(0);
        return env.evaluate(new Atom(List.of(new Atom("lambda"), params, maxSimplify(env, D(maxSimplify(env, code), v)))));
    }

    @Override
    public Atom apply(Executor env, List<Atom> arguments) {
        if(arguments.size() != 1 && arguments.size() != 2)
            throw new Error("Invalid invocation to 'D'.");
        return new Atom(new LbcSupplier<>(() -> {
            arguments.get(0).guardType("Argument to 'D'", Type.CLOSURE);
            String v;
            if(arguments.size() == 2)
                v = arguments.get(1).getString().get();
            else
                v = "x";
            return deriv(env, arguments.get(0).getClosure().get(), v).get().get();
        }));
    }
}
