/*===============================================================+
 |               0-ZeT JavaScript Library for Web       [ 1.0 ]  |
 |                                   / anton.baukin@gmail.com /  |
 +===============================================================*/


// +----: ZeT Mini-Core :----------------------------------------+

if(window.ZeT)
	throw new Error('Double defined ZeT library!')

/**
 * The sections of the listing are organized so that each
 * following section may depend on items defined previously.
 * This zero section depends only on Lodash library.
 */
var ZeT = window.ZeT =
{
	keys             : Object.keys,

	/**
	 * Extends optional object with optional extension.
	 * Assigns only own properties of the object.
	 * Returns the extended object.
	 */
	extend           : function(obj, ext)
	{
		if(!obj) obj = {}
		if(!ext) return obj

		if(Object.assign) //?: {modern}
			return Object.assign(obj, ext)

		//~: copy manually
		var keys = ZeT.keys(ext)
		for(var i = 0;(i < keys.length);i++)
		obj[keys[i]] = ext[keys[i]]

		return obj
	},

	/**
	 * Invokes the function given. Optional arguments
	 * must go before the function-body. This-context
	 * of the call is passed to the callback.
	 */
	scope            : function(/* [parameters] f */)
	{
		var f = arguments[arguments.length - 1]
		if(!(typeof f === 'function'))
			throw new Error('ZeT.scope() got not a function!')

		//?: {has additional arguments}
		for(var a = [], i = 0;(i < arguments.length - 1);i++)
			a.push(arguments[i])

		return (a.length)?(f.apply(this, a)):(f.call(this))
	},

	/**
	 * Directly concatenates to string items of array-like
	 * object starting with the index given.
	 */
	cati             : (function(/* index, array-like */)
	{
		var con = String.prototype.concat
		var tos = Object.prototype.toString

		function isx(o)
		{
			return (o === null) || (typeof o === 'undefined')
		}

		function isn(n)
		{
			return (typeof n === 'number') ||
			  (tos.call(n) === '[object Number]')
		}

		function isi(i)
		{
			if(!isn(i))
				return false

			//?: {is short integer}
			if(i === (i|0))
				return true

			return (i == Math.floor(i))
		}

		function iss(s)
		{
			return (typeof s === 'string')
		}

		function isf(f)
		{
			return (typeof f === 'function')
		}

		function isa(a)
		{
			return Array.isArray(a) ||
			  (!isx(a) && isi(a.length) && !iss(a) && !isf(a))
		}

		return function(index, objs)
		{
			if(!objs || !isi(objs.length))
				return ''

			for(var i = 0;(i < objs.length);i++)
				if((i < index) || isx(objs[i]))
					objs[i] = ''
				else if(isa(objs[i]))
					objs[i] = ZeT.cati(0, objs[i])

			return con.apply('', objs)
		}
	})(),

	/**
	 * Returns (as a string) current JS call stack.
	 * Optional integer argument allows to take only
	 * the leading lines of result.
	 */
	stack            : function(n)
	{
		var s = '' + new Error().stack
		if(!ZeT.isi(n)) return s

		//~: split & splice
		if((s = s.split('\n')).length > n)
			s.splice(n, s.length - n)

		return s.join('\n')
	}
}


// +----: ZeT Checks :-------------------------------------------+

ZeT.extend(ZeT,
{
	/**
	 * Strict testing of strings. Note that this check also
	 * handles case when string is coerced to raw object
	 * in calls like apply('s', ...)
	 */
	iss              : ZeT.scope(function(/* s */)
	{
		var s2s = String.prototype.toString

		return function(s)
		{
			return (typeof s === 'string') || (s && (s.toString === s2s))
		}
	}),

	/**
	 * Returns false for not a string objects, or for
	 * strings that are whitespace-trimmed empty.
	 */
	ises             : function(s)
	{
		return !ZeT.iss(s) || !s.length || !/\S/.test(s)
	},

	isf              : function(f)
	{
		return (typeof f === 'function')
	},

	/**
	 * Is plain object (having no prototype).
	 */
	iso              : ZeT.scope(function(/* o */)
	{
		var f2s = Function.prototype.toString
		var  oc = f2s.call(Object)

		return function(o)
		{
			//?: {not an object-like}
			if(!o || !(typeof o === 'object'))
				return false

			//~: get the prototype
			var p = Object.getPrototypeOf(o)
			if(!p) return true

			//~: constructor
			var c = p.hasOwnProperty('constructor') &&
			  p.constructor

			return ZeT.isf(c) && (c instanceof c) &&
			  (f2s.call(c) == oc)
		}
	}),

	/**
	 * Is plain object, or an object having prototype.
	 */
	isox             : function(o)
	{
		return !!o && (typeof o === 'object') && !ZeT.isa(o)
	},

	/**
	 * Strict variant of boolean test.
	 */
	isb              : function(b)
	{
		return (b === true) || (b === false)
	},

	isu              : function(o)
	{
		return (typeof o === 'undefined')
	},

	/**
	 * First variant of call takes single arguments
	 * and returns true when it's undefined or null.
	 *
	 * Second, takes:
	 *
	 * [0] value to check;
	 * [1] object to test;
	 * ... properties path.
	 *
	 * If the object is undefined or null, returns true.
	 * If the path to the destination property is given
	 * (each path element as a distinct argument), goes
	 * into the object. If any intermediate member is
	 * undefined or null, or the final property is,
	 * return true.
	 *
	 * When final member is defined checks it (soft ==)
	 * against the given value: returns the check result.
	 *
	 * Sample. ZeT.isx(true, opts, 'a', 0, 'b')
	 * returns true when opts, or opts.a, or opts.a[0],
	 * or opts.a[0].b are undefined or null, or final
	 * (opts.a[0].b == true).
	 *
	 * Also, if value to check is a function, invokes
	 * it on the final member instead of equality,
	 * and with undefined value when intermediate
	 * member is undefined or null.
	 */
	isx              : ZeT.scope(function()
	{
		function isux(o)
		{
			return (o === null) || (typeof o === 'undefined')
		}

		function i$x(check, o)
		{
			//?: {comparator}
			if(ZeT.isf(check))
				return check(o)

			//?: {is undefined | soft equality}
			return ZeT.isu(o) || (check == o)
		}

		return function()
		{
			//?: {single value to check}
			var l = arguments.length
			if(l <= 1) return isux(arguments[0])

			//~: initial object to check
			var o = arguments[1]
			if(isux(o)) return true

			//~: trace to the target member
			for(var k, i = 2;(i < l);i++)
			{
				//?: {has the key undefined}
				if(isux(k = arguments[i]))
					return undefined

				//?: {has the object undefined}
				if(isux(o = o[k]))
					break
			}

			return i$x(arguments[0], o)
		}
	}),

	isa              : Array.isArray,

	/**
	 * Test is array-like object. It is an array,
	 * or object that has integer length property,
	 * except string and functions.
	 */
	isax             : function(x)
	{
		return ZeT.isa(x) || (!ZeT.isx(x) &&
		  ZeT.isi(x.length) && !ZeT.iss(x) && !ZeT.isf(x))
	},

	isn              : ZeT.scope(function(/* n */)
	{
		var tos = Object.prototype.toString

		return function(n)
		{
			return (typeof n === 'number') ||
			  (tos.call(n) === '[object Number]')
		}
	}),

	/**
	 * Is integer number.
	 */
	isi              : function(i)
	{
		if(!ZeT.isn(i))
			return false

		//?: {is short integer}
		if(i === (i|0))
			return true

		return (i == Math.floor(i))
	},

	/**
	 * Returns true if the argument is defined, not false, 0, not
	 * ws-empty string or empty array, or array-like object having
	 * an item like that. (Up to one level of recursion only!)
	 *
	 * Warning as a sample: if you test agains an array that
	 * contains 0, false, null, undefined, ws-empty string,
	 * or an empty array (just empty) — test fails!
	 */
	test             : ZeT.scope(function(/* x */)
	{
		function notdef(x)
		{
			return (x === null) || (x === false) ||
			  (x === 0) || (typeof x === 'undefined') ||
			  (ZeT.iss(x) && ZeT.ises(x)) ||
			  (ZeT.isa(x) && !x.length)
		}

		return function(x)
		{
			//?: {root check is undefined}
			if(notdef(x)) return false

			//?: {root check is not array-like}
			if(!ZeT.isax(x)) return true

			//~: check all the items of array-like are defined
			for(var i = 0;(i < x.length);i++)
				if(!notdef(x[i])) return true

			return false //<-- array is empty
		}
	})
})


// +----: ZeT Asserts :------------------------------------------+

ZeT.extend(ZeT,
{
	/**
	 * Returns exception concatenating the optional
	 * arguments into string message. The stack is
	 * appended as string after the new line.
	 */
	ass              : function(/* messages */)
	{
		var m = ZeT.cati(0, arguments)
		var x = ZeT.stack()

		//?: {has message}
		if(!ZeT.ises(m)) x = m.concat('\n', x)

		//!: return error to throw later
		return new Error(x)
	},

	/**
	 * First argument of assertion tested with ZeT.test().
	 * The following optional arguments are the message
	 * components concatenated to string.
	 *
	 * The function returns the test argument.
	 */
	assert           : function(test /* messages */)
	{
		if(ZeT.test(test)) return test

		var m = ZeT.cati(1, arguments)
		if(ZeT.ises(m)) m = 'Assertion failed!'

		throw ZeT.ass(m)
	},

	/**
	 * Checks that given object is not null, or undefined.
	 */
	assertn          : function(obj /* messages */)
	{
		if(!ZeT.isx(obj)) return obj

		var m = ZeT.cati(1, arguments)
		if(ZeT.ises(m)) m = 'The object is undefined or null!'

		throw ZeT.ass(m)
	},

	/**
	 * Tests the the given object is a function
	 * and returns it back.
	 */
	assertf          : function(f /* messages */)
	{
		if(ZeT.isf(f)) return f

		var m = ZeTS.cati(1, arguments)
		if(ZeT.ises(m)) m = 'A function is required!'

		throw ZeT.ass(m)
	},

	/**
	 * Tests that the first argument is a string
	 * that is not whitespace-empty. Returns it.
	 */
	asserts          : function(str /* messages */)
	{
		if(!ZeT.ises(str)) return str

		var m = ZeT.cati(1, arguments)
		if(ZeT.ises(m)) m = 'Not a whitespace-empty string is required!'

		throw ZeT.ass(m)
	},

	/**
	 * Tests the the given object is a not-empty array
	 * and returns it back.
	 */
	asserta          : function(array /* messages */)
	{
		if(ZeT.isa(array) && array.length)
			return array

		var m = ZeTS.cati(1, arguments)
		if(ZeT.ises(m)) m = 'Not an empty array is required!'

		throw ZeT.ass(m)
	}
})


// +----: ZeT Basics :-------------------------------------------+

ZeT.extend(ZeT,
{
	/**
	 * Defines global object in the window scope.
	 * If factory argument is undefined, returns
	 * previously defined instance. The factory
	 * is never called twice.
	 *
	 * When name has '.', they are used to trace
	 * the intermediate objects from the window top.
	 * 'ZeT.S' means that 'S' object is nested into
	 * 'ZeT' that is in the window (global) scope.
	 * Each intermediate object must exist.
	 */
	init             : function(name, factory)
	{
		ZeT.asserts(name, 'ZeT difinitions are for string names only!')
		var scope = window, xname = name, i = name.indexOf('.')

		//~: trace the scope
		while(i != -1)
		{
			var n = name.substring(0, i)
			name = name.substring(i + 1)

			//?: {has empty name parts}
			ZeT.asserts(n, 'Empty intermediate name in ZeT.define(', xname, ')!')
			ZeT.asserts(name, 'Empty terminating name in ZeT.define(', xname, ')!')

			//?: {has the scope undefined}
			ZeT.assertn(scope = scope[n], 'Undefined intermediate scope object ',
			  'in ZeT.define(', xname, ') at [', n, ']!')

			i = name.indexOf('.')
		}

		//?: {target exists in the scope}
		var o = scope[name]
		if(!ZeT.isx(o)) return o

		//~: assign the target
		if(!ZeT.isx(factory))
			scope[name] = o = ZeT.assertf(factory)()

		return o
	},

	/**
	 * Invokes ZeT.init() with the object instead of a factory.
	 */
	define           : function(name, object)
	{
		return ZeT.init(name, ZeT.isx(object)?(undefined):
		  function(){ return object })
	},

	defined          : function(name)
	{
		ZeT.assert(arguments.length == 1)
		return ZeT.init(name)
	},

	/**
	 * Creates object having the same prototype.
	 *
	 * Warning! The class function of the object
	 * is not the same!
	 */
	proto            : function(obj)
	{
		ZeT.assertn(obj)
		var p = ZeT.assertn(Object.getPrototypeOf(obj))

		function P(){}
		P.prototype = p

		return new P()
	},

	/**
	 * Takes any array-like object and returns true array.
	 * If source object is an array, returns it as-is.
	 *
	 * Array-like objects do have integer length property
	 * and values by the integer keys [0; length).
	 *
	 * Note that strings are not treated as array-like.
	 * ZeT.a('...') returns ['...']. The same for functions.
	 *
	 * If object given is not an array, wraps it to array.
	 * Undefined or null value produces empty array.
	 *
	 * If source object has toArray() method, that method
	 * is invoked with this-context is the object.
	 */
	a                : function(a)
	{
		if(ZeT.isa(a)) return a
		if(ZeT.isu(a) || (a === null)) return []
		if(ZeT.iss(a) || ZeT.isf(a)) return [a]

		if(ZeT.isf(a.toArray))
		{
			ZeT.assert(ZeT.isa(a = a.toArray()),
			  'ZeT.a(): .toArray() returned not an array!')
			return a
		}

		//~: manually copy the items
		var l = a.length; if(!ZeT.isi(l)) return [a]
		var r = new Array(l)
		for(var i = 0;(i < l);i++) r[i] = a[i]

		return r
	},

	not              : function(f)
	{
		return function()
		{
			return !f.apply(this, arguments)
		}
	},

	and              : function(/* functions */)
	{
		var fs = ZeT.a(arguments)

		return function()
		{
			for(var i = 0;(i < fs.length);i++)
				if(!fs[i].apply(this, arguments))
					return false

			return true
		}
	},

	or               : function(/* functions */)
	{
		var fs = ZeT.a(arguments)

		return function()
		{
			for(var i = 0;(i < fs.length);i++)
				if(fs[i].apply(this, arguments))
					return true

			return false
		}
	},

	/**
	 * Shortcut to (s.indexOf(x) >= 0).
	 */
	ii               : function(s /* x0, x1, ... */)
	{
		if(!s) return false

		for(var i = 1;(i < arguments.length);i++)
			if(s.indexOf(arguments[i]) >= 0)
				return true

		return false
	}
})


// +----: ZeT Strings  :-----------------------------------------+

var ZeTS = ZeT.define('ZeT.S',
{
	iss              : ZeT.iss,

	ises             : ZeT.ises,

	/**
	 * Trims side white spaces of the string given.
	 * Returns empty string as a fallback.
	 */
	trim             : function(s)
	{
		return (!ZeT.iss(s) || !s.length)?(''):(s.replace(/^\s+|\s+$/g, ''))
	},

	first            : function(s)
	{
		return (ZeT.iss(s) && s.length)?(s.charAt(0)):(undefined)
	},

	/**
	 * The first arguments is a string to inspect.
	 * The following (one or more) is the argument
	 * strings: function returns true when inspected
	 * string starts with any of the arguments.
	 */
	starts           : function(s)
	{
		if(!ZeT.iss(s)) return undefined

		for(var i = 1;(i < arguments.length);i++)
			if(s.indexOf(arguments[i]) == 0)
				return true

		return false
	},

	/**
	 * Analogue of ZeT.starts(), but checks end.
	 */
	ends             : function(s)
	{
		if(!ZeT.iss(s)) return undefined

		var x, l = s.length
		for(var i = 1;(i < arguments.length);i++)
			if(s.lastIndexOf(x = arguments[i]) == l - x.length)
				return true

		return false
	},

	/**
	 * Replaces plain string with else plain string.
	 */
	replace          : function(s, a, b)
	{
		return s.split(a).join(b)
	},

	cati             : ZeT.cati,

	/**
	 * Directly concatenates given objects into a string.
	 */
	cat              : function(/* various objects */)
	{
		return ZeTS.cati(0, arguments)
	},

	/**
	 * Concatenates trailing objects if the first one
	 * passes ZeT.test().
	 */
	catif            : function(x /* various objects */)
	{
		return ZeT.test(x)?ZeTS.cati(1, arguments):('')
	},

	/**
	 * Shortcut for ZeTS.catif() that checks all the arguments.
	 */
	catifall         : function(/* various objects */)
	{
		for(var i = 0;(i < arguments.length);i++)
			if(!ZeT.test(arguments[i]))
				return ''

		return ZeTS.cati(0, arguments)
	},

	/**
	 * Concatenates the objects with the separator given
	 * as the first argument, or as 'this' context object.
	 * Note that arrays are processed deeply.
	 */
	catsep           : function(/* sep, various objects */)
	{
		var x, b = 1, s = '', sep = arguments[0]

		//?: {invoked with string 'this'}
		if(ZeT.iss(this)) { b = 0; sep = this }

		//c: for each argument
		for(var i = b;(i < arguments.length);i++)
		{
			if(ZeT.isx(x = arguments[i]))
				continue

			if(!ZeT.iss(x))
			{
				//?: {is an array}
				if(ZeT.isa(x))
					x = ZeTS.catsep.apply(sep, x)
				//?: {toString()}
				else if(ZeT.isf(x.toString))
					x = x.toString()
				else
					x = Object.prototype.toString.call(x)
			}

			//?: {empty string}
			if(ZeT.ises(x))
				continue

			if(s.length) s += sep
			s += x
		}

		return s
	},

	/**
	 * Invokes callback for each sub-string in the string.
	 * If callback returns false, breaks. Optional separator
	 * (defaults to /\s+/) argument to String.split().
	 */
	each             : function(/* [sep], s, f */)
	{
		var sep, s, f

		ZeT.scope(arguments.length, arguments, function(l, a)
		{
			ZeT.assert(l == 2 || l == 3)
			if(l == 2) { sep = /\s+/; s = a[0]; f = a[1] }
			else { sep = a[0]; s = a[1]; f = a[2] }
		})

		ZeT.assert(ZeT.iss(s))
		ZeT.assertf(f)

		s = s.split(sep)
		for(var i = 0;(i < s.length);i++)
			if(s[i].length)
				if(f(s[i]) === false)
					return this

		return this
	}
})


// +----: ZeT Arrays  :------------------------------------------+

var ZeTA = ZeT.define('ZeT.A',
{

	/**
	 * Creates a copy of array-like object given.
	 * Optional [begin; end) range allows to copy
	 * a part of the array. Negative values of
	 * the range boundaries are not allowed.
	 */
	copy             : function(a, begin, end)
	{
		//?: {has no range}
		if(ZeT.isu(begin))
			return ZeT.isa(a)?(a.slice()):ZeT.a(a)

		//?: {end is undefined}
		if(ZeT.isu(end) || (end > a.length))
			end = a.length

		//~: asserts on [begin; end)
		ZeT.assert(ZeT.isi(begin) && ZeT.isi(end))
		ZeT.assert((begin >= 0) && (begin <= end))

		//?: {is an array exactly}
		if(ZeT.isa(a)) return a.slice(begin, end)

		//~: manual copy
		var r = new Array(end - begin)
		for(var i = begin;(i < end);i++)
			r[i - begin] = a[i]
		return r
	},

	/**
	 * Removes the items from the target array.
	 * If item is itself an array, recursively
	 * invokes this function.
	 *
	 * Items are checked with indexOf() equality
	 * (put it to array, then check it is there).
	 * Undefined and null items are supported.
	 *
	 * Returns the target array.
	 */
	remove           : ZeT.scope(function(/* array, item, ... */)
	{
		var u = {}, n = {}

		function collect(m, a)
		{
			if(ZeT.isu(a)) return m[u] = true
			if(a === null) return m[n] = true

			if(!ZeT.isax(a))
				return m.push(a)

			for(var i = 0;(i < a.length);i++)
				collect(m, a[i])
		}

		function test(m, x)
		{
			if(ZeT.isu(x)) x = u
			if(x === null) x = n
			return (m.indexOf(x) >= 0)
		}

		return function(a)
		{
			var m = [], r = []

			//~: collect the keys
			for(var i = 1;(i < arguments.length);i++)
				collect(m, arguments[i])

			//~: scan for ranged splicing
			for(i = 0;(i < a.length);i++)
				if(test(m, a[i]))
				{
					//~: scan for the range
					for(var j = i + 1;(j < a.length);j++)
						if(!test(m, a[j])) break;

					r.push(i)
					r.push(j - i)
					i = j //<-- advance
				}

			//~: back splicing
			for(var i = r.length - 2;(i >= 0);i -= 2)
				a.splice(r[i], r[i+1])

			return a //<-- target array
		}
	}),

	/**
	 * Takes two array-like objects and optional
	 * [begin, end) range from the second one.
	 *
	 * If the first (target) object is an array,
	 * modifies it adding the items from the
	 * second object in the range given.
	 *
	 * If the target object is not an array,
	 * makes it's array-copy, returns it.
	 */
	concat           : function(a, b, begin, end)
	{
		a = ZeT.a(a)

		//?: {has range} make a copy
		if(ZeT.isu(begin)) b = ZeT.a(b); else
			b = ZeTA.copy(b, begin, end)

		//~: push all the items
		Array.prototype.push.apply(a, b)
		return a
	},

	/**
	 * Checks that two objects are array-like and
	 * have the same length and the items each
	 * strictly (===) equals.
	 */
	eq               : function(a, b)
	{
		if(a === b) return true
		if(ZeT.isx(a) || ZeT.isx(b))
			return (ZeT.isx(a) == ZeT.isx(b))

		//?: {not array-like}
		if(!ZeT.isi(a.length) || !ZeT.isi(b.length))
			return false

		//?: {length differ}
		var l = a.length
		if(l != b.length)
			return false

		for(var i = 0;(i < l);i++)
			if(a[i] !== b[i])
				return false
		return true
	}
})


// +----: ZeT Extends :------------------------------------------+

ZeT.extend(ZeT,
{
	/**
	 * Makes shallow copy of the source with
	 * optional extension provided. Supports
	 * only plain objects.
	 */
	clone            : function(src, ext)
	{
		ZeT.assertn(src)

		var r; if(ZeT.iso(src))
			r = ZeT.extend({}, src)

		if(ZeT.iso(ext))
			r = ZeT.extend(r, ext)

		return r
	},

	/**
	 * Clone deeply object with prototype support.
	 *
	 * It directly copies fields of this types: numbers,
	 * booleans, functions, not a plain objects.
	 * Arrays are copied deeply.
	 */
	deepClone        : function(obj)
	{
		//?: {undefined, null, false, zero}
		if(!obj) return obj

		//?: {is string} copy it
		if(ZeT.iss(obj)) return '' + obj

		//?: {is an array}
		var i, res; if(ZeT.isa(obj))
		{
			res = new Array(obj.length)
			for(i = 0;(i < obj.length);i++)
				res[i] = ZeT.deepClone(obj[i])
			return res
		}

		//?: {not a plain object}
		if(!ZeT.isox(obj)) return obj

		//~: extend
		var res = ZeT.proto(obj), keys = ZeT.keys(obj)
		for(i = 0;(i < keys.length);i++)
			res[keys[i]] = ZeT.deepClone(obj[keys[i]])

		return res
	},

	/**
	 * The same as ZeT.extend(), but copies only
	 * the fields that are undefined in the source.
	 */
	xextend          : function(obj, src)
	{
		if(!src) return obj
		if(!obj) obj = {}

		//?: {not an object}
		ZeT.assert(ZeT.isox(obj),
		  'ZeT.extendx(): not an object! ')

		var k, keys = ZeT.keys(src)
		for(var i = 0;(i < keys.length);i++)
			//?: {field is undefined}
			if(ZeT.isu(obj[k = keys[i]]))
				obj[k] = src[k]

		return obj
	},

	/**
	 * Takes object and copies all the fields from the source
	 * when the same fields are undefined (note that nulls are
	 * not undefined). If field is a plain object, extends
	 * it deeply. Note that arrays are not merged!
	 * A deep clone of a field value is assigned.
	 */
	deepExtend       : function(obj, src)
	{
		if(!src) return obj
		if(!obj) obj = {}

		//?: {not an object}
		ZeT.assert(ZeT.isox(obj),
		  'ZeT.deepExtend(): not an object! ')

		var k, keys = ZeT.keys(src)
		for(var i = 0;(i < keys.length);i++)
			//?: {field is undefined}
			if(ZeT.isu(obj[k = keys[i]]))
				obj[k] = ZeT.deepClone(src[k])
			//?: {extend nested object}
			else if(ZeT.isox(obj[k]))
				ZeT.deepExtend(obj[k], src[k])

		return obj
	},

	/**
	 * Analogue of deep extend, but takes the required
	 * array of properties to allow to assign. Fields
	 * of the nested objects are '.' separated in
	 * the nesting hierarchy.
	 *
	 * Note that the properties array is updated, and the
	 * same instance may be given on the following calls
	 * to speed up the processing.
	 */
	deepAssign       : ZeT.scope(function()
	{
		function assignLevel(l, obj, src, ps)
		{
			var p = ps[l]  //<-- property
			var o = obj[p] //<-- target
			var s = src[p] //<-- source

			//?: {source is undefined} skip
			if(ZeT.isu(s)) return

			//?: {target is undefined} just set
			if(ZeT.isu(o)) return obj[p] = s

			//?: {source is not a plain object} just set
			if(!ZeT.iso(s)) return obj[p] = s

			//?: {target is not a plain object} just set
			if(!ZeT.iso(o)) return obj[p] = s

			//?: {properties depth reached}
			if(l + 1 == ps.length) return

			assignLevel(l + 1, o, s, ps)
		}

		return function(obj, src, ps)
		{
			ZeT.assert(ZeT.isox(obj))
			ZeT.assert(ZeT.isox(src))
			ZeT.asserta(ps)

			for(var i = 0;(i < ps.length);i++)
			{
				var p; if(!ZeT.isa(p = ps[i]))
					ps[i] = p = ZeT.asserts(p).split('.')

				assignLevel(0, obj, src, p)
			}

			return obj
		}
	}),

	/**
	 * Tells whether the object given has only
	 * these own attributes. If object is an array,
	 * it is checked deeply!
	 */
	isonly           : ZeT.scope(function(/* o, attr, ... */)
	{
		function check(o)
		{
			if(ZeT.isx(o)) return true

			ZeT.assert(ZeT.isox(o))
			ZeT.assert(arguments.length > 1)

			var keys = {} //~: map existing keys
			ZeT.each(o, function(v, k){ keys[k] = true})

			//~: off the keys given
			for(var i = 1;(i < arguments.length);i++)
				keys[arguments[i]] = false

			//~: check the keys left
			var found = false
			ZeT.each(keys, function(v){
				if(v) return !(found = true)
			})

			return !found
		}

		return function(o)
		{
			if(!ZeT.isax(o))
				return check.apply(this, arguments)

			for(var i = 0;(i < o.length);i++)
			{
				arguments[0] = o[i]
				if(!ZeT.isonly.apply(this, arguments))
					return false
			}

			return true
		}

	}),

	/**
	 * Takes an object, or an array-like and goes
	 * deeply in it by the names, or integer indices,
	 * or else object-keys given as the arguments.
	 */
	get              : function(/* object, properties list */)
	{
		var o = arguments[0]
		if(ZeT.isx(o)) return o

		for(var k, i = 1;(i < arguments.length);i++)
		{
			//?: {has the key undefined}
			if(ZeT.isx(k = arguments[i]))
				return undefined

			//?: {has the object undefined}
			if(ZeT.isx(o = o[k]))
				return undefined
		}

		return o
	},

	/**
	 * Returns a function having 'this' assigned to 'that'
	 * argument and the following arguments passed as
	 * the first arguments of each call.
	 *
	 * 0   [required] a function;
	 * 1   [required] 'this' context to use;
	 * 2.. [optional] first and the following arguments.
	 */
	fbind            : function(f, that)
	{
		//?: {has function and the context}
		ZeT.assert(ZeT.isf(f))
		ZeT.assertn(that)

		//~: copy the arguments
		var args = ZeTA.copy(arguments, 2)

		return function()
		{
			var a = ZeTA.concat(ZeTA.copy(args), arguments)
			return f.apply(that, a)
		}
	},

	/**
	 * Works as ZeT.fbind(), but takes additional
	 * arguments as a copy of array-like object given.
	 * If the arguments are restricted, no more from
	 * the call instance are added.
	 */
	fbinda           : function(f, that, args, restrict)
	{
		//?: {has function and the context}
		ZeT.assertf(f)
		ZeT.assertn(that)

		//~: copy the arguments
		args = ZeTA.copy(args)

		return function()
		{
			var a = ZeTA.copy(args)

			if(restrict !== true)
				a = ZeTA.concat(a, arguments)

			return f.apply(that, a)
		}
	},

	/**
	 * Universal variant of ZeT.fbind(). Second argument
	 * may be 'this' context. Else arguments are 0-indexed
	 * followed by the value.
	 */
	fbindu           : function(f /*, [this], (i, arg)... */)
	{
		//?: {has function and the context}
		ZeT.assert(ZeT.isf(f))

		var that = arguments[1], iarg = []

		//?: {with this-context}
		var i = 1; if(arguments.length%2 == 0) i = 2; else
			that = undefined

		//~: copy following arguments
		while(i < arguments.length)
		{
			ZeT.assert(ZeT.isi(arguments[i]))
			ZeT.assert(arguments[i] >= 0)
			iarg.push(arguments[i])
			ZeT.assert(i + 1 < arguments.length)
			iarg.push(arguments[i+1])
			i += 2
		}

		return function()
		{
			var a = ZeT.a(arguments)
			for(i = 0;(i < iarg.length);i += 2)
				a.splice(iarg[i], 0, iarg[i+1])

			return f.apply(ZeT.isu(that)?(this):(that), a)
		}
	},

	/**
	 * Trailing argument must be a function that
	 * is invoked only when all leading arguments
	 * do pass ZeT.test().
	 *
	 * Returns null when callback was not invoked,
	 * or the result of the function call.
	 */
	scopeif          : function(/* args, f */)
	{
		var a = ZeT.a(arguments)
		ZeT.assert(arguments.length)

		var f = a.pop()
		ZeT.assert(ZeT.isf(f))

		for(var i = 0;(i < a.length);i++)
			if(!ZeT.test(a[i])) return null

		return f.apply(this, a)
	},

	/**
	 * Evaluates the script given in a function body.
	 * If optional arguments object is given, executes
	 * script with all the variables defined from it.
	 */
	xeval            : function(script, args)
	{
		//?: {has no script}
		if(ZeT.ises(script)) return

		//?: {has no argumets}
		if(!args) return eval('((function(){'.
		  concat(script, '})());'))

		//~: access the keys
		ZeT.assert(ZeT.iso(args))
		var ps = ZeT.keys(args)

		//~: create temporary reference
		var ti = ZeT.xeval.$index
		if(!ti) ZeT.xeval.$index = ts = new Date().getTime()
		ti = 'ZeT$xeval$temp$' + (++ti)
		window[ti] = args

		try
		{
			//~: build the script
			var s = '((function(){'
			ZeT.each(ps, function(p) {
				s += ZeTS.cat(p, ' = ', ti, '.', p, ';')
			})
			s = s.concat(script, '})());')

			//!: evaluate
			return eval(s)
		}
		finally
		{
			delete window[ti]
		}
	},

	/**
	 * Takes array-like object and invokes the
	 * function given on each item. Function
	 * receives arguments: [0] is the item,
	 * [1] is the item index.
	 *
	 * This-context of the function call
	 * is also the item iterated.
	 *
	 * If call on some item returns false, iteration
	 * is breaked and that stop-index is returned.
	 *
	 * This function also supports general objects
	 * that do pass ZeT.isox(). In this case iteration
	 * takes place over all own ZeT.keys(), the key
	 * is given as the second argument (as index).
	 * The call returns all the keys processed as
	 * and array, or single key had been rejected.
	 */
	each             : ZeT.scope(function(/* array | object, f */)
	{
		function eacha(a, f)
		{
			for(var i = 0;(i < a.length);i++)
				if(f.call(a[i], a[i], i) === false)
					return i

			return a.length
		}

		function eacho(o, f)
		{
			var keys = ZeT.keys(o), k = keys[0]

			for(var i = 0;(i < keys.length);k = keys[++i])
				if(f.call(o[k], o[k], k) === false)
					return k

			return keys
		}

		return function(o, f)
		{
			ZeT.assertf(f)

			if(ZeT.isax(o))
				return eacha(o, f)

			if(ZeT.isox(o))
				return eacho(o, f)
		}
	}),

	/**
	 * Invokes the function given over each not
	 * undefined item of the array-like object.
	 * Returns array of not undefined results.
	 *
	 * Instead of a function you may give anything
	 * like property-key object (name, index, ...).
	 *
	 * Callback has the same arguments as ZeT.each().
	 */
	map              : function(a, f)
	{
		//?: {collect a property}
		var p; if(!ZeT.isf(p = f))
			f = function(x) { return x[p] }

		var r = []; ZeT.each(a, function(x, i)
		{
			if(ZeT.isu(x)) return
			x = f.call(x, x, i)
			if(!ZeT.isu(x)) r.push(x)
		})

		return r
	},

	/**
	 * Converts given object to JSON formatted string.
	 */
	o2s              : function(o)
	{
		return JSON.stringify(o)
	},

	/**
	 * Converts given JSON formatted string to an object.
	 */
	s2o              : function(s)
	{
		return (ZeT.isx(s) || ZeT.ises(s))?(null):JSON.parse(s)
	}
})


// +----: ZeT.Class :--------------------------------------------+

/**
 * Creates Class instance. The arguments are:
 *
 * 0  [optional] parent Class instance;
 * 1  [optional] body object with the Class methods.
 *
 * The parent Class may be of ZeT implementation: each such
 * instance is marked with (Class.ZeT$Class = true).
 *
 * It is allowed the parent Class to be a general Function.
 * As for ZeT Class inheritance, Function.prototype will be
 * the parent [[Prototype]] of Class.prototype. To call Function
 * (as a constructor) from Class initialization method (see
 * Class.initializer() method), use the same $superApply() or
 * $superCall() runtime-added methods.
 *
 * The body object may contain not only the methods, but properties
 * of other types: they are 'static' members of the prototype of
 * the instances created.
 *
 * The returned Class instance is a Function having the following
 * instance members:
 *
 * · static : empty Object
 *
 *   use this object to store data shared for every instance
 *   of this Class.
 *
 * · create(...) : new instance of Class
 *
 *   creates an instance of the Class. Takes any number of arguments
 *   that are passed as-is to the initialization method.
 *
 * · extend({body} | [{body}]) : this Class
 *
 *   adds the methods (and the properties) of the body (or array of
 *   bodies) given to the prototype of the Class. Note that the methods
 *   (as references) are copied wrapped, and adding methods (or fields)
 *   to the body object after extending has no effect.
 *
 * · addMethod(name, f) : this Class
 *
 *   adds the method given to the prototype of Class. Note that the
 *   function given is wrapped to provide $-objects at the call time.
 *
 * · initializer([names]) : this Class
 *
 *   give an array of names (or single name) with the body' initialization
 *   method. Default names are: 'initialize', 'init', and 'constructor'.
 *   Only the first method found in the instance is called.
 *
 *   Note that constructor() is always defined when plain Function was
 *   inherited Hence, 'constructor' must be the last in the list, or you
 *   have to implement constructor() as the initializing method.
 *
 * The instances created as a Class has the following properties and methods:
 *
 * · $class  it's Class instance.
 *
 * · $plain  equals to a plain Function when it is the root of hierarchy;
 *
 * · $callSuper(), $applySuper()
 *
 *   these functions are available only within a method call.
 *   They invoke the method with the same name defined in the
 *   ancestor classes hierarchy.
 *
 * · $callContext
 *
 *   as $callSuper(), available only within a method call.
 *   It contains the following properties:
 *
 *   · name:  the name of the method (currently invoked);
 *
 *   · wrapped:  the original method added to Class (and wrapped);
 *
 *   · method: method is being invoked (i.e., the wrapper);
 *
 *   · callSuper, applySuper:  functions that are assigned
 *     as $- to object when invoking a method;
 *
 *   · superFallback: function to invoke within $call-,
 *     $applySuper() when super method was not found.
 *
 *   Note that $callContext object is shared between the calls
 *   of the body method wrapped! (Each function in the method
 *   hierarchy still has it's own instance.)
 */
ZeT.Class = ZeT.define('ZeT.Class', function()
{
	//~: initialization methods lookup array
	var inits = ['initialize', 'init', 'constructor']

	//!: the Class instance to return
	function Class()
	{
		//c: process the initialize names list
		for(var i = 0;(i < inits.length);i++)
		{
			var m = this[inits[i]]
			if(!ZeT.isf(m)) continue

			//?: {this is a root Function constructor} skip it for now
			if(Class.$plain && (m === Class.$plain.prototype.constructor))
				continue

			//?: {this is Object constructor}
			if(m === Object.prototype.constructor) continue

			//~: install fallback for plain Function root
			if(Class.$plain && m.$callContext)
				m.$callContext.superFallback = Class.$plain

			//!: call the initializer
			return m.apply(this, arguments)
		}

		//HINT: we found no initialization method in the body...

		//?: {has hierarchy root Function} invoke it as a fallback
		if(Class.$plain)
			Class.$plain.apply(this, arguments)
	}

	//:: Class.static
	Class.static = {}

	//:: Class.$super
	Class.$super = ZeT.isf(arguments[0])?(arguments[0]):(null)

	//:: Class.$plain
	if(Class.$super) Class.$plain = (Class.$super.ZeT$Class === true)?
	  (Class.$super.$plain):(Class.$super)

	//?: {has parent class} use it as a prototype
	Class.prototype = (!Class.$super)?{}:ZeT.scope(function()
	{
		function U() {}
		U.prototype = Class.$super.prototype
		return new U()
	})

	//:: Class.create()
	Class.create = function()
	{
		var args = arguments

		function C()
		{
			Class.apply(this, args)
		}

		C.prototype = Class.prototype
		return new C()
	}

	function createCallContext(name, f)
	{
		return { name: name, wrapped : f,

			assign  : function(that)
			{
				//:: this.$callContext
				that.$callContext = this

				//:: this.$callSuper
				that.$callSuper  = this.callSuper

				//:: this.$applySuper
				that.$applySuper = this.applySuper
			},

			revoke  : function(that)
			{
				delete that.$callContext
				delete that.$callSuper
				delete that.$applySuper
			}
		}
	}

	//:: Class.addMethod()
	Class.addMethod = function(name, f)
	{
		//~: find super method and invalidate it's cache marker
		var sx, sm = Class.$super && Class.$super.prototype[name]
		if(ZeT.isf(sm)) sm.$cacheMarker = sx = {}
			else sm = undefined

		function accessSuper(that)
		{
			//?: {has super method & the marker is actual}
			if(sm && (sm.$cacheMarker === sx))
				return sm

			//~: find it
			sm = Class.$super && Class.$super.prototype[name]
			if(ZeT.isf(sm)) sx = sm.$cacheMarker; else
			{
				sm = undefined

				//?: {has fallback call provided}
				var fb = that.$callContext.superFallback
				if(fb) return fb

				throw new Error('$super method (' + name + ') not found!')
			}

			return sm
		}

		//~: invalidate cache marker of existing method
		ZeT.scope(function()
		{
			var m = Class.prototype[name]
			if(ZeT.isf(m)) m.$cacheMarker = {}
		})

		//~: wrap the method
		function Method()
		{
			//HINT: when method is invoked recursively,
			//  it has the same call context

			var x = this.$callContext //<-- current call context
			var a = !x || (x.method !== Method) //?: is it changed

			try
			{
				//?: {new call context must be assigned}
				if(a) Method.$callContext.assign(this)

				//!: invoke the function is being wrapped
				return f.apply(this, arguments)
			}
			finally
			{
				//?: {has new call context assigned}
				if(a) try
				{
					this.$callContext.revoke(this)
				}
				finally
				{
					//?: {has external context} return to it
					if(x) x.assign(this)
				}
			}
		}

		//~: assign wrapper to the prototype
		Class.prototype[name] = Method

		//:: Class.[Method].$callContext
		Method.$callContext = createCallContext(name, f)
		Method.$callContext.method = Method

		//:: Class.[Method].$callSuper
		Method.$callContext.callSuper = function()
		{
			return accessSuper(this).apply(this, arguments)
		}

		//:: Class.[Method].$applySuper
		Method.$callContext.applySuper = function(args)
		{
			return accessSuper(this).apply(this, args)
		}

		return Class
	}

	function isStaticMember(x)
	{
		return !ZeT.isf(x) || (x.ZeT$Class === true)
	}

	//:: Class.extend()
	Class.extend = function(body)
	{
		if(!body) return Class
		if(!ZeT.isa(body)) body = [body]

		for(var j = 0;(j < body.length);j++)
		{
			var b = body[j], k, v, ks = ZeT.keys(b), p = Class.prototype
			for(var i = 0;(i < ks.length);i++)
			{
				k = ks[i]; v = b[k]
				if(isStaticMember(v)) p[k] = v; else
					Class.addMethod(k, v)
			}
		}

		return Class
	}

	//~: extend with the body given
	Class.extend((Class.$super)?(arguments[1]):(arguments[0]))

	//:: Class.initializer()
	Class.initializer = function(a)
	{
		if(a && !ZeT.isa(a)) a = [a]
		if(ZeT.isa(a) && a.length)
			inits = a
		return Class
	}

	//:: this.$class
	Class.prototype.$class = Class

	//~: mark as a Class instance
	Class.ZeT$Class = true

	return Class
})


ZeT.extend(ZeT,
{
	/**
	 * Tells that given object (function)
	 * is a class of ZeT implementation.
	 */
	isclass          : function(c)
	{
		return ZeT.isf(c) && (c.ZeT$Class === true)
	},

	/**
	 * ZeT.define() class. The arguments are:
	 *
	 * 0   ZeT.define key name of the class;
	 *
	 * 1   parent Class, function, or string
	 *     definition name (optional);
	 *
	 * 2   class body object (optional).
	 *
	 * If there is no parent class, give
	 * class body as [1] argument.
	 */
	defineClass      : function()
	{
		ZeT.assert(arguments.length >= 1)
		ZeT.assert(arguments.length <= 3)

		//~: access the class already defined
		var cls, name = arguments[0]
		if(cls = ZeT.defined(name)) return cls

		//~: take the parent class defined
		var args = [], pOb = arguments[1]
		if(ZeT.iss(pOb)) pOb = ZeT.assertn(
		  ZeT.defined(pOb), 'Parent class definition [',
		  pOb, '] is not found!')

		if(arguments.length == 3)
		{
			var body = arguments[2]
			ZeT.assert(ZeT.isf(pOb) && ZeT.iso(body))
			args = [ pOb, body ]
		}
		else if(arguments.length == 2)
		{
			ZeT.assert(ZeT.isf(pOb) || ZeT.iso(pOb))
			args = [ pOb ]
		}

		//~: create a class
		return ZeT.define(name, ZeT.Class.apply(ZeT.Class, args))
	},

	/**
	 * Creates instance of the defined or directly
	 * given ZeT Class.
	 *
	 * 0   definition key name or Class object;
	 * 1.. passed to class constructor.
	 */
	createInstance   : function()
	{
		//~: access class definition
		var cls = arguments[0]
		if(ZeT.iss(cls)) cls = ZeT.defined(cls)

		//?: {not a ZeT.Class}
		ZeT.assert(ZeT.isclass(cls),
		 'Can not create instance of not a ZeT.Class!')

		//~: remove 0-argument (definition name)
		return cls.create.apply(cls, ZeTA.copy(arguments, 1))
	},

	/**
	 * ZeT.define() instance of ZeT Class given.
	 *
	 * 0   string define-key of instance;
	 * 1.. passed to createInstance().
	 */
	defineInstance   : function()
	{
		//~: lookup it is already defined
		var res = ZeT.defined(arguments[0])
		if(res) return res

		//~: remove 0-argument (definition name)
		var args = ZeTA.copy(arguments, 1)
		res = ZeT.createInstance.apply(this, args)

		//~: define it
		return ZeT.define(arguments[0], res)
	},

	/**
	 * Extends the class (also, by it's definition name)
	 * with the body-methods given. Body may also contain
	 * else values to be included in Class.static.
	 * Only string keys are allowed!
	 */
	extendClass      : function(cls, ext)
	{
		//~: access defined class
		if(ZeT.iss(cls)) cls = ZeT.defined(cls)

		ZeT.assert(ZeT.isclass(cls), 'A ZeT.Class is required to be extended!')
		ZeT.assertn(ext, 'Class extention is not given!')

		//c: extend for each key
		ZeT.each(ZeT.keys(ext), function(key)
		{
			ZeT.asserts(key, 'Not a string key of a body member: ', key)

			var p; if(ZeT.isf(p = ext[key]))
				cls.addMethod(key, p)
			else
				cls.static[key] = p
		})

		return cls
	},

	/**
	 * ZeT.define() instance of a temporary
	 * (anonymous) sub-class of ZeT Class
	 * or constructing function.
	 *
	 * 0   string define-key of instance;
	 *
	 * 1   [optional] definition key name, or Class,
	 *     or plain function to be the parent class
	 *     of the temporary one;
	 *
	 * 2   the body of the class;
	 *
	 * 3.. [optional] arguments of the class constructor
	 *     to create temporary instance.
	 */
	singleInstance   : function()
	{
		//~: lookup it is already defined
		var res = ZeT.defined(arguments[0])
		if(res) return res

		//~: access the parent class defined
		var parent = arguments[1]; if(ZeT.iss(parent))
			parent = ZeT.assertf( ZeT.defined(parent),
			  'Can not create instance of not a Class or function!')

		//~: arguments of class create invocation
		var cargs = ZeT.isf(parent)?([parent, arguments[2]]):([arguments[1]])

		//~: create the anonymous class
		var cls = ZeT.Class.apply(ZeT.Class, cargs)

		//~: copy constructor arguments
		var args = ZeTA.concat([cls], arguments, ZeT.isf(parent)?(3):(2))

		//~: create and define the instance
		var obj = ZeT.createInstance.apply(ZeT, args)
		return ZeT.define(arguments[0], obj)
	},

	/**
	 * Creates anonymous sub-class of the class given by it's
	 * Class instance or the definition name, extends it with
	 * the body methods given and passes the optional arguments
	 * to the instance constructor.
	 *
	 * The first variant of the arguments is:
	 *
	 * [0] ZeT Class or definition key;
	 * [1] arguments array;
	 * [2] sub-class definition body.
	 *
	 * The second variant is:
	 *
	 * [0] ZeT Class or definition key;
	 * [1] sub-class definition body;
	 * ... arguments list (optional).
	 */
	hiddenInstance   : function()
	{
		var cls = arguments[0]

		//?: {parent class is a definition name}
		if(ZeT.iss(cls)) cls = ZeT.assertn(ZeT.defined(cls),
		  'No definition is bound by the name [', cls, ']!')

		ZeT.assert( ZeT.isf(cls), //?: {not a function}
		  'Can not create instance of not a Class or function!')

		//~: take the body
		var args, body = arguments[1]
		if(ZeT.isa(body))
		{
			args = body
			body = arguments[2]
		}

		ZeT.assert(ZeT.iso(body), //?: {body is not an object}
		  'Anonymous class body is not an object!')

		//~: create the anonymous class
		var cls  = ZeT.Class.call(ZeT.Class, cls, body)

		//~: copy constructor arguments
		if(args) args = ZeTA.concat([cls], args)
		else     args = ZeTA.concat([cls], arguments, 2)

		//~: create the instance
		return ZeT.createInstance.apply(ZeT, args)
	}
})


// +----: Linked Map :-------------------------------------------+

/**
 * Class that implements a linked Map: mapping that
 * remembers the order of placing items in it.
 */
ZeT.Map = ZeT.defineClass('ZeT.LinkedMap',
{
	init             : function()
	{
		this.clear()
	},

	clear            : function()
	{
		this.map   = {}
		this.lasti = 1
		this.size  = 0
	},

	/**
	 * Adds the value into the map. Returns
	 * previous value. New value becomes
	 * the last in the order.
	 *
	 * If value is undefined, assignes it
	 * to be the same as key! This treats
	 * linked map as a linked set.
	 */
	put              : function(k, v)
	{
		var y, x = this.map[ZeT.assertn(k)]

		//?: {has no value}
		if(ZeT.isu(v)) v = k

		if(x)
		{
			y = x.value
			x.value = v
		}
		else
		{
			this.map[k] = x = { key: k, value: v }
			this.size++
		}

		this.$tail(x)
		return y
	},

	get              : function(k)
	{
		var x = this.map[ZeT.assertn(k)]
		return x && x.value
	},

	remove           : function(k)
	{
		var x; if(!(x = this.map[ZeT.assertn(k)]))
			return undefined

		this.size--

		//~: remove from the sequence
		this.$extract(x)

		//~: delete the entry
		delete this.map[k]

		return x.value
	},

	/**
	 * Returns integer index of order of the item
	 * mapped by the key. The very first index is 1.
	 * The range of indices is sparce (with holes)!
	 */
	index            : function(k)
	{
		var x = this.map[k]
		return x && x.index
	},

	contains         : function(k)
	{
		return !!this.map[k]
	},

	/**
	 * Invokes callback function over all existing
	 * items in the order of putting them. The call
	 * is exactly the same as in ZeT.each().
	 */
	each             : function(f)
	{
		for(var x = this.head;(x);x = x.next)
			if(false === f.call(x.value, x.value, x.key))
				return x.key
	},

	/**
	 * Iterates over entities in the reversed order.
	 */
	reverse          : function(f)
	{
		for(var x = this.tail;(x);x = x.prev)
			if(false === f.call(x.value, x.value, x.key))
				return x.key
	},

	$tail            : function(x)
	{
		x.index = ++this.lasti

		if(!this.tail)
		{
			ZeT.assert(!this.head)
			return this.head = this.tail = x
		}

		if(this.tail == x)
			return

		this.$extract(x)

		x.prev = this.tail
		this.tail.next = x
		this.tail = x
	},

	$extract         : function(x)
	{
		var p = x.prev
		var n = x.next

		if(this.head == x)
		{
			ZeT.assert(!p)
			this.head = n
		}
		else if(p)
			p.next = n

		if(this.tail == x)
		{
			ZeT.assert(!n)
			this.tail = p
		}
		else if(n)
			n.prev = p

		x.prev = x.next = null
	}
})


// +----: ZeT for Browser :--------------------------------------+

ZeT.extend(ZeT,
{
	/**
	 * Marks function as a delayed property.
	 */
	delay            : function(f)
	{
		ZeT.assertf(f, 'Zet.delay() may not delay not a function!')
		f.ZeT$delay = true
		return f
	},

	defineDelay      : function(name, f)
	{
		return ZeT.define(name, ZeT.delay(f))
	},

	isDelayed        : function(obj)
	{
		return ZeT.isf(obj) && (obj.ZeT$delay === true)
	},

	/**
	 * Resolves all delayed (own) properties
	 * of the object given without going deeply.
	 *
	 * If a property argument is defined, takes
	 * only it without updating the source object.
	 */
	undelay          : function(obj, p)
	{
		if(!ZeT.isox(obj))
			return obj

		if(!ZeT.isx(p)) //?: {take single property}
		{
			var o = obj[p]
			return (ZeT.isf(o) && (o.ZeT$delay === true))?o():(o)
		}

		ZeT.each(obj, function(x, k)
		{
			if(ZeT.isf(x) && (x.ZeT$delay === true))
				obj[k] = x()
		})

		return obj
	},

	/**
	 * Creates a function that sequentially calls the
	 * functions given as the arguments.
	 *
	 * All the functions share the same call context.
	 * The arguments of the pipe call are given to the
	 * first function. The result is then given as the
	 * arguments of the next call as arguments array!
	 * So when the result is an array, and you want it
	 * to come as the first argument only, but is not
	 * split into the arguments, wrap it in array.
	 *
	 * Warning: if intermediate result is undefined
	 * or null, the pipe processing is stopped!
	 *
	 * The result of the last call is returned as is.
	 */
	pipe             : function(/* functions */)
	{
		var fn = ZeT.a(arguments)

		//?: {has just one item in the pipe}
		ZeT.assert(fn.length, 'ZeT.pipe() functions are not defined!')
		if(fn.length == 1) return fn[0]

		return function()
		{
			var r = ZeT.a(arguments) //<-- intermediate result

			for(var i = 0;(i < fn.length);i++)
			{
				//?: {previous results are not an array}
				if(!ZeT.isa(r)) r = [r] //<-- wrap for apply

				//~: invoke the i-th function of the pipe
				r = fn[i].apply(this, r)

				//?: {has no result}
				if(ZeT.isu(r) || (r === null))
					return r
			}

			return r
		}
	},

	/**
	 * Shorthand for setTimeout() function that takes
	 * the function given and optionally binds it with
	 * this-context and the arguments array given.
	 *
	 * Returns setTimeout() id.
	 */
	timeout          : function(tm, f, that, args)
	{
		ZeT.assert(ZeT.isn(tm) && (tm >= 0), 'ZeT.timeout(): illegal timeout!')
		ZeT.assert(ZeT.isf(f), 'ZeT.timeout(): not a function!')

		//?: {do bind}
		if(that) f = ZeT.fbinda(f, that, args)

		return setTimeout(f, tm)
	},

	/**
	 * Returns function that on-call activates timeout
	 * for the function given as the first argument.
	 * Arguments are the same as in ZeT.timeout().
	 */
	timeouted        : function()
	{
		var args = ZeT.a(arguments)

		return function()
		{
			return ZeT.timeout.apply(ZeT, args)
		}
	},

	/**
	 * Logs the values provided and returns the value:
	 *
	 * · if there are no arguments, all are undefined,
	 *   or ws-empty strings, returns undefined;
	 *
	 * · the last argument being not a string;
	 *
	 * · the last not empty string.
	 */
	log              : ZeT.scope(function(/* objects */)
	{
		function isxlog(o)
		{
			if(!o) return false

			//?: {is plain object}
			if(ZeT.isox(o)) return true

			//?: {is class}
			if(ZeT.isclass(o)) return true

			//?: {is an element}
			if(o.nodeType === 1) return true

			//?: {is array having non-logged}
			if(ZeT.isa(o))
				for(var i = 0;(i < o.length);i++)
					if(isxlog(o[i]))
						return true

			return false
		}

		function pack(a, j, i)
		{
			if(j + 1 >= i) return i

			for(var x = '', k = j;(k < i);k++)
				if(!ZeT.isx(a[k]))
					x += a[k]

			a[j] = x; a.splice(j + 1, i - j - 1)
			return j
		}

		function ise(x)
		{
			return ZeT.isx(x) || (ZeT.iss(x) && ZeT.ises(x))
		}

		return function()
		{
			var r, j = 0, a = ZeT.a(arguments)

			//~: result --> last not a string
			for(var i = a.length - 1;(i >= 0);i--)
				if(!ZeT.isx(a[i]) && !ZeT.iss(a[i]))
					{ r = a[i]; break }

			//~: result --> last not a ws-empty string
			if(!r) for(i = a.length - 1;(i >= 0);i--)
				if(!ZeT.ises(a[i]))
					{ r = a[i]; break }

			for(i = 0;(i < a.length);i++)
				if(isxlog(a[i]))
				{
					i = pack(a, j, i)
					j = i + 1
				}

			pack(a, j, a.length)

			var empty = true
			for(j = 0;(j < a.length);j++)
				if(!ise(a[j])) { empty = false; break }

			if(!empty) console.log.apply(console, a)
			return r
		}
	})
})


// +----: ZeT XML  :---------------------------------------------+

var ZeTX = ZeT.define('ZeT.X',
{
	isnode           : function(node)
	{
		return !!node && ZeT.isf(node.getElementsByTagName)
	},

	/**
	 * Returns direct children of the XML node given.
	 */
	nodes            : function(node, name)
	{
		//?: {can't search}
		if(!ZeTX.isnode(node)) return undefined

		var res = [], nodes = node.getElementsByTagName(name)
		if(nodes) for(var i = 0;(i < nodes.length);i++)
			if(nodes[i].parentNode == node)
				res.push(nodes[i])

		return res
	},

	/**
	 * Returns first direct child of the XML node given.
	 */
	node             : function(node, name)
	{
		if(!node) return ubdefined
		var res = ZeTX.nodes(node, name)
		return (res && res.length)?(res[0]):(null)
	},

	attr             : function(node, attr)
	{
		return node && ZeT.isf(node.getAttribute) &&
		  node.getAttribute(attr)
	},

	/**
	 * Returns the text values of the node immediate
	 * children with text and CDATA types.
	 */
	text             : function(node)
	{
		if(!node) return node

		//?: {text || cdata}
		if((node.nodeType === 3) || (node.nodeType === 4))
			return node.nodeValue

		var val, res = []

		if(node.nodeType !== 1) return undefined
		node = node.firstChild

		while(node)
		{
			//?: {text || cdata}
			if((node.nodeType === 3) || (node.nodeType === 4))
				val = node.nodeValue
			if(ZeT.iss(val)) res.push(val)

			node = node.nextSibling
		}

		return String.prototype.concat.apply('', res)
	}
})