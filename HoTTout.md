---
title: HoTT
layout: page
---

## Provingground - Basic Homotopy Type theory implementation

These notes concern the object _HoTT_, which has the core implementation of homotopy type theory.

The major components of homotopy type theory implemented in the object HoTT are

* Terms, types and Universes.
* Function and dependent function types.
* λs.
* Pairs and Dependent pairs.
* Disjoint union types.
* Types 0 and 1 and an object in the latter.
* Identity types

Inductive types, induction and recursion are in different objects as they are rather subtle. The other major way (also not in the _HoTT_ object) of constructing non-composite types is to wrap scala types, possibly including symbolic algebra.


The _core_ project contains code that is agnostic to how it is run. In particular this also compiles to scala-js.




### Universes, Symbolic types

We have a family of universes, but mostly use the first one denoted by Type. Given a type, we can construct symbolic objects of that type. We construct such a type _A_.



```scala
scala>  

scala> import provingground._ 


scala> import HoTT._ 


scala> val A = "A" :: Type 


scala> A == Type.::("A") 

```



We consider a symbolic object of the type _A_



```scala
scala> val a = "a" :: A 

```




## Function types, lambdas, Identity

Given types A and B, we have the function type A → B. An element of this is a function from A to B.

We can construct functions using λ's. Here, for the type _A_, we construct the identity on _A_ using a lambda. We can then view this as a dependent function of _A_, giving the identity function.

In this definition, two λ's are used, with the method _lmbda_ telling the TypecompilerType that the result is a (non-dependent) function.



```scala
scala> val id = lambda(A)(lmbda(a)(a)) 

```




The type of the identity function is a mixture of Pi-types and function types. Which of these to use is determined by checking dependence of the type of the value on the varaible in a λ-definition.



```scala
scala> id.typ 


scala> lmbda(a)(a).typ 


scala> lmbda(a)(a).typ.dependsOn(A) 

```




The lambdas have the same effect at runtime. It is checked if the type of the value depends on the variable.
The result is either _LambdaFixed_ or _Lambda_ accordingly.



```scala
scala> val indep = lmbda(a)(a) 


scala> val dep = lambda(a)(a) 


scala> indep == dep 

```



Note that we have alternative notation for lambdas, the maps to methods `:->` and `:~>`.
For instance, we can define the identity using these.


```scala
scala> assert(id == A :~> (a :-> a))
```



### Hygiene for λs

A new variable object, which has the same toString, is created in making lambdas. This is to avoid name clashes.



```scala
scala> val l = dep.asInstanceOf[LambdaFixed[Term, Term]] 


scala> l.variable 


scala> l.variable == a 

```



## Modus Ponens

We construct Modus Ponens, as an object in Homotopy Type theory. Note that A ->: B is the function type A → B.



```scala
scala> val B = "B" :: Type 


scala>  

scala> val f = "f" :: (A ->: B) 


scala>  

scala> val mp = lambda(A)(lambda(B)(lmbda(a)(lmbda(f)(f(a))))) 

```



The type of Modus Ponens is again a mixture of Pi-types and function types.



```scala
scala> mp.typ 

```




We can apply modus ponens with the roles of _A_ and _B_ reversed. This still works because variable clashes are avoided.



```scala
scala> val mpBA = mp(B)(A) 


scala> mpBA.typ == B ->: (B ->: A) ->: A 

```




### Equality of λs

Lambdas do not depend on the name of the variable.



```scala
scala> val aa = "aa" :: A 


scala> lmbda(aa)(aa) == lmbda(a)(a) 


scala> (lmbda(aa)(aa))(a) == a 

```




## Dependent types

Given a type family, we can construct the corresponding Pi-types and Sigma-types. We start with a formal type family, which is just a symbolic object of the appropriate type.



```scala
scala> val Bs = "B(_ : A)" :: (A ->: Type) 

```




### Pi-Types

In addition to the case class constructor, there is an agda/shapeless-like  convenience method for constructing Pi-types. Namely, given a type expression that depends on a varaible _a : A_, we can construct the Pi-type correspoding to the obtained λ-expression.

Note that the !: method just claims and checks a type, and is useful (e.g. here) for documentation.



```scala
scala> val fmly = (a !: A) ~>: (Bs(a) ->: A) 

```




### Sigma-types

There is also a convenience method for defining Sigma types using λs.



```scala
scala> Sgma(a !: A, Bs(a)) 

```






```scala
scala> Sgma(a !: A, Bs(a) ->: Bs(a) ->: A) 

```




## Pair types

Like functions and dependent functions, pairs and dependent pairs can be handled together. The _mkPair_ function assignes the right type after checking dependence, choosing between pair types, pairs and dependent pairs.



```scala
scala> val ba = "b(a)" :: Bs(a) 


scala> val b = "b" :: B 


scala> mkPair(A, B) 


scala> mkPair(a, b) 


scala> mkPair(a, b).typ 


scala> mkPair(a, ba).typ 

```





```scala
scala> mkPair(A, B).asInstanceOf[ProdTyp[Term, Term]] 

```




## Plus types

We can also construct the plus type _A plus B_, which comes with two inclusion functions.



```scala
scala> val AplusB = PlusTyp(A, B) 

```





```scala
scala> AplusB.incl1(a) 

```





```scala
scala> AplusB.incl2 

```



In the above, a λ was used, with a variable automatically generated. These have names starting with $ to avoid collision with user defined ones.

## Identity type

We have an identity type associated to a type _A_, with reflexivity giving terms of this type.



```scala
scala> val eqAa = IdentityTyp(A, a, a) 


scala> val ref = Refl(A, a) 

```





```scala
scala> ref.typ == eqAa 

```




## The Unit and the  Nought

Finally, we have the types corresponding to _True_ and _False_



```scala
scala> Unit 


scala> Zero 


scala> Star !: Unit 

```


