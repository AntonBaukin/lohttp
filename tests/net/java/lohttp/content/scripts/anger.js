/*===============================================================+
 |                Event Driven Angular Directives                |
 |                                   / anton.baukin@gmail.com /  |
 +===============================================================*/

ZeT.scope(angular.module('anger', []), function(anger)
{
	/**
	 * Anger directives prefix.
	 */
	var ANGER = 'ag'

	/**
	 * Anger directives prefix utility.
	 */
	function ag(name, attr)
	{
		ZeT.asserts(name)

		if(attr === true)
			return ANGER + '-' + name

		//~: camel case '-'
		var cc = name.split('-')
		for(var i = 0;(i < cc.length);i++)
			cc[i] = cc[i].substring(0, 1).toLocaleUpperCase() +
			  cc[i].substring(1)

		return ANGER + cc.join('')
	}

	var CMD_SYMBOLS = '!?{'

	function cmdSym(s)
	{
		var x = ZeTS.first(ZeT.asserts(s))
		return ZeT.ii(CMD_SYMBOLS, x)?(x):(undefined)
	}

	function cmdArg(s)
	{
		var x = ZeTS.first(ZeT.asserts(s))
		return !ZeT.ii(CMD_SYMBOLS, x)?(s):(s.substring(1))
	}

	function cmdCall(s, f)
	{
		ZeT.asserts(s)
		ZeT.assertf(f)

		return function()
		{
			var args = ZeT.a(arguments)
			args.unshift(cmdSym(s))
			return f.apply(this, args)
		}
	}

	function parseAttrEvents(events, scope, f)
	{
		var self = this

		ZeT.asserts(events)
		ZeT.assertn(scope)
		ZeT.assertf(f)

		//?: {event is an object}
		if(events.charAt(0) == '{')
		{
			events = scope.$eval(events)
			ZeT.assert(ZeT.isox(events))

			ZeT.each(events, function(o, s)
			{
				f.call(self, [ s, o ])
			})

			return
		}

		//~: iterate over regular string
		ZeTS.each(events, function(s)
		{
			f.call(self, s)
		})
	}

	function eachAttrEvent(an, f)
	{
		ZeT.asserts(an)
		ZeT.assertf(f)

		return function(scope, node, attr)
		{
			var self = this
			var   ax = ZeTS.trim(ZeT.asserts(attr[ag(an)]))
			var args = ZeT.a(arguments)
			args.unshift('')

			parseAttrEvents(ax, scope, function(s)
			{
				args[0] = s
				f.apply(self, args)
			})
		}
	}

	//~: template id
	anger.directive(ag('template-id'), function()
	{
		return {
			restrict   : 'A',

			template   : function($element, $attrs)
			{
				//~: the template id
				var id = ZeT.asserts($attrs[ag('template-id')])

				//~: display back
				$element.css('display', '')

				//~: remove the id attribute
				$element.removeAttr(ag('template-id', true))

				//~: save the html
				$element.data(ag('template-html'), $element[0].outerHTML)

				//~: hide back
				$element.css('display', 'none')

				//~: write id to the node
				$element.attr('id', ag('template-id', true) + '-' + id)

				return ''
			}
		}
	})

	//~: include template from node found by the id
	anger.directive(ag('template'), function()
	{
		return {
			restrict   : 'A',

			template   : function($element, $attrs)
			{
				//~: the template id
				var id = ZeT.asserts($attrs[ag('template')])

				//~: find the template node
				var  n = $('#' + ag('template-id', true) + '-' + id).first()

				//?: {the node does not exist}
				ZeT.assert(n.length == 1, 'Node #',
				  ag('template-id', true), '-', id, ' is not found!')

				return ZeT.asserts(n.data(ag('template-html')))
			}
		}
	})

	//~: creates isolated scope from the object
	anger.directive(ag('scope'), function()
	{
		return {
			restrict   : 'A',
			scope      : true,

			controller : function($scope, $attrs)
			{
				var s = $attrs[ag('scope')]
				if(ZeT.ises(s)) return
				ZeT.extend($scope, $scope.$eval(s))
			}
		}
	})

	//~: trim (whitespaces between tags)
	anger.directive(ag('trim'), function($timeout)
	{
		function filter()
		{
			return (this.nodeType == 3) && !/\S/.test(this.nodeValue)
		}

		function clear()
		{
			$(this).contents().filter(filter).remove()
			$(this).children().each(clear)
		}

		return function(scope, node)
		{
			clear.apply(node)
		}
	})

	//~: visible-on
	anger.directive(ag('visible-on'), function()
	{
		return eachAttrEvent('visible-on', function(s, scope, node, attr)
		{
			scope.$on(cmdArg(s), cmdCall(s, function(x, event)
			{
				node.toggle(ZeTS.first(x) != '!')
			}))
		})
	})

	//~: focus-on
	anger.directive(ag('focus-on'), function()
	{
		return eachAttrEvent('focus-on', function(s, scope, node, attr)
		{
			scope.$on(cmdArg(s), cmdCall(s, function(x, event)
			{
				if((x == '?') && !ZeTS.ises(node.val()))
					return //<-- skip fields with value

				//?: {default prevented}
				if(event.defaultPrevented)
					return

				event.preventDefault()
				ZeT.timeout(10, function(){ node.focus() })
			}))
		})
	})

	//~: class
	anger.directive(ag('class'), function()
	{
		return eachAttrEvent('class', function(s, scope, node, attr)
		{
			if(!ZeT.isa(s)) return

			var e = ZeT.asserts(s[0])
			var c = s[1]; if(ZeT.iss(c)) c = [ c ]
			ZeT.asserta(c)

			scope.$on(e, function(event)
			{
				ZeT.each(c, function(cls)
				{
					if(ZeTS.first(cls) != '!')
						node.addClass(cls)
					else
						node.removeClass(cls.substring(1))
				})
			})
		})
	})

	//~: init: passes $element
	anger.directive(ag('init'), function()
	{
		return function(scope, node, attr)
		{
			var script = attr[ag('init')]

			if(!ZeT.ises(script))
				ZeT.xeval(script, {
					$scope   : scope,
					$element : node
				})
		}
	})

	//~: on: reacts on listed events
	anger.directive(ag('on'), function()
	{
		return eachAttrEvent('on', function(s, scope, node, attr)
		{
			scope.$on(cmdArg(s), cmdCall(s, function(x, event)
			{
				var script = attr[ag('on-' + s)]

				if(!ZeT.ises(script))
					ZeT.xeval(script, {
						$scope   : scope,
						$element : node
					})
			}))
		})
	})

	function opts(node, p)
	{
		var opts = $(node).data('ag-opts')
		if(!opts) return

		ZeT.assert(ZeT.iso(opts))
		return ZeT.isu(p)?(opts):(opts[p])
	}

	anger.directive(ag('opts'), function()
	{
		return function(scope, node, attrs)
		{
			var opts = ZeTS.trim(attrs[ag('opts')])
			if(ZeT.ises(opts)) return

			ZeT.assert(ZeTS.first(opts) == '{')
			opts = scope.$eval(opts)
			ZeT.assert(ZeT.iso(opts))

			node.data('ag-opts', opts)
		}
	})

	function eachAttrEventHandler(h, an, action, wrapper)
	{
		ZeT.assertf(h)
		ZeT.asserts(an)
		ZeT.assertf(action)

		var eae = eachAttrEvent(an, action)

		return function(/* scope, node, ... */)
		{
			var self = this, args = ZeT.a(arguments)

			h.call(this, args, [an, action, wrapper], function()
			{
				if(!ZeT.isf(wrapper))
					eae.apply(self, args)
				else
				{
					var a = ZeTA.copy(args)

					a.unshift(function() {
						return eae.apply(self, args)
					})

					wrapper.apply(self, a)
				}
			})
		}
	}

	function eachAttrEventClick(an, action, wrapper)
	{
		function h(args, aaw, f)
		{
			$(args[1]).click(f)
		}

		function ifclick(eae, scope, node)
		{
			var ifc = opts(node, 'if')
			if(ZeT.isf(ifc)) ifc = ifc.call(scope, node)

			if(ifc !== false)
				if(ZeT.isf(wrapper))
					return wrapper.apply(this, arguments)
				else
					return eae.apply(this, arguments)
		}

		return eachAttrEventHandler(h, an, action, ifclick)
	}

	function scopeUp(scope, up)
	{
		ZeT.assertn(scope)
		ZeT.assert(ZeT.isi(up))

		while(up-- > 0)
			if(!scope.$parent) break; else
				scope = scope.$parent

		return scope
	}

	function eventBroadcast(s, scope, node)
	{
		var up = opts(node, 'up')
		if(ZeT.isi(up)) scope = scopeUp(scope, up)

		if(ZeT.isa(s))
		{
			//ZeT.log("> ", s[0], ': ', s[1], ' to $', scope.$id)
			scope.$broadcast(s[0], s[1])
		}
		else
		{
			//ZeT.log("> ", s, ' to $', scope.$id)
			scope.$broadcast(s)
		}
	}

	//~: click broadcast
	anger.directive(ag('click'), function()
	{
		return eachAttrEventClick('click', eventBroadcast)
	})

	//~: key enter broadcast
	anger.directive(ag('key-enter'), function()
	{
		function h(args, aaw, f)
		{
			$(args[1]).keypress(function(e)
			{
				if(e.which == 13) f()
			})
		}

		return eachAttrEventHandler(h, 'key-enter', eventBroadcast)
	})

	/**
	 * Traverses the scopes tree starting with the given
	 * node, then going to all it's descendants.
	 *
	 * Traversing goes by the tree levels the number
	 * as the second argument of the callback.
	 *
	 * When callback returns false, traversing is finished
	 * with the result of that node.
	 */
	function traverse(scope, f)
	{
		var l = 0, level = [ scope ]

		//c: while there are levels
		while(level.length)
		{
			//c: ask for the level accumulated
			for(var i = 0;(i < level.length);i++)
				//?: {stop by current}
				if(false === f(level[i], l))
					return level[i]

			var next = [] //<-- the next level

			//c: accumulate the next level
			for(i = 0;(i < level.length);i++)
			{
				var x = level[i].$$childHead

				while(x)
				{
					next.push(x)
					x = x.$$nextSibling
				}
			}

			//~: swap the level
			level = next; l++
		}
	}

	//~: history handling strategy
	var History = anger.History =
	{
		HID       : '29aa6a6a-3c51-11e6-ac61-9e71128cae77',

		replace   : function(/* fname, scope, obj */)
		{
			window.history.replaceState(
			  History.state.apply(History, arguments), '')
		},

		push      : function(/* fname, scope, obj */)
		{
			var s = History.state.apply(History, arguments)
			var x = window.history.state

			//?: {state is not a duplicate}
			if(ZeT.o2s(x) != ZeT.o2s(s))
				window.history.pushState(s, '')
		},

		$roots    : {},

		state     : function(fname, scope, obj)
		{
			ZeT.asserts(fname)
			ZeT.assertn(scope)

			//?: {the scope given is temporary}
			if(scope.$$transcluded == true)
				return { uuid: History.HID, f: 'noop' }

			//~: the root scope
			var id, rs = ZeT.assertn(scope.$root)

			//~: search for the already saved
			ZeT.each(History.$roots, function(k, s){
				if(s === rs) { id = k; return false }
			})

			if(!id) //?: {root scopes is not saved}
			{
				id = '' + new Date().getTime()
				ZeT.assert(!History.$roots[id])
				History.$roots[id] = rs

				//!: potential memory leak

				//~: watch the scope destroyed
				rs.$on('$destroy', function() {
					delete History.$roots[id]
				})
			}

			return {
				uuid : History.HID,
				f    : fname,
				rid  : id,
				sid  : scope.$id,
				obj  : obj
			}
		},

		call      : function(e)
		{
			//?: {not our state}
			var s = ZeT.get(e, 'originalEvent', 'state')
			if(!ZeT.iso(s) || s.uuid != History.HID) return

			var f = ZeT.assertf(History[s.f])
			f.call(History, s)
		},

		noop      : function(){},

		broadcast : function(s)
		{
			var events = ZeT.asserts(ZeT.get(s, 'obj', 'events'))
			var   node = s.obj.node
			if(ZeT.iss(node)) node = $('#' + node)

			//~: the root scope
			var rs = ZeT.assertn(History.$roots[s.rid],
			  'No root scope $', s.rid, ' is found in History!')

			//~: traverse the root scope
			var scope; traverse(rs, function(x)
			{
				if(x.$id == s.sid) { scope = x; return false }
			})

			//?: {scope is not found}
			ZeT.assertn(scope, 'History referred Angular ',
			  'scope $id = ', s.scope, ' is not found!')

			//~: broadcast the events
			parseAttrEvents(events, scope, function(s){
				eventBroadcast(s, scope, node)
			})
		}
	}

	//~: react on module generated history states
	$(window).on('popstate', History.call)

	function nodeId(node)
	{
		var n = $(node).first()
		ZeT.assert(1 === n.length)

		var id = node.attr('id')
		if(!ZeT.ises(id)) return id

		if(!anger.$nodeId) anger.$nodeId = 1
		node.attr('id', id = (ANGER + '-' + anger.$nodeId++))

		return id
	}

	//~: click push to history
	anger.directive(ag('click-history'), function()
	{
		function wrapper(f, scope, node, attrs)
		{
			History.push('broadcast', scope, {
			  node: nodeId(node),
			  events: attrs[ag('click-history')],
			})

			f()
		}

		return eachAttrEventClick('click-history',
		  eventBroadcast, wrapper)
	})
})