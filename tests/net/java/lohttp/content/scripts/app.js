/*===============================================================+
 |                      Application Script                       |
 +===============================================================*/

ZeT.scope(angular.module('main', [ 'anger' ]), function(module)
{
	var INITAL = 'login'
	var anger  = angular.module('anger')

	//~: root controller
	module.controller('root', function($scope, $timeout)
	{
		function displayInitialPage(want, e, page)
		{
			if(page != ZeTS.cat('pages/', want, '.html'))
				return //<-- not the wanted page

			$timeout(function()
			{
				$scope.$broadcast('content-' + want)

				//~: save initial point in the history
				anger.History.replace('broadcast', $scope,
				  { events: 'content-hide content-' + want })
			})
		}

		//!: set the first displayed block
		$scope.$on('$includeContentLoaded', ZeT.fbindu(
		  displayInitialPage, 0, INITAL))

		//~: history back
		$scope.historyBack = function()
		{
			ZeT.assert(window.history.length > 0)
			window.history.back()
			$scope.$broadcast('fog-close')
		}
	})

	/**
	 * Deeply assignes the value
	 */
	function setif(/* object, path... , predicate | value */)
	{
		ZeT.assert(arguments.length >= 3)
		ZeT.assert(ZeT.isox(arguments[0]))

		var a = arguments, o = a[0]
		var p = a[a.length - 1]

		//?: {evaluate the predicate}
		if(ZeT.isf(p))
			p = p(o)

		//?: {is not a value}
		if(!ZeT.test(p))
			return false

		//~: deep assign cycle
		for(var i = 1;i + 1 < a.length;i++)
		{
			var s = ZeT.asserts(a[i])

			if(i + 2 == a.length)
				o[s] = p
			else if(ZeT.isox(o[s]))
				o = o[s]
			else
			{
				ZeT.assert(ZeT.isx(o[s]))
				o[s] = o = {}
			}
		}

		return p
	}

	//~: login page controller
	module.controller('login', function($scope, $rootScope, $element)
	{
		//~: do sign in
		$scope.signIn = function()
		{
			//~: collect the errors
			delete $scope.errors
			ZeT.each([ 'login', 'pass' ], function(f) {
				setif($scope, 'errors', f, ZeT.ises($scope[f]))
			})

			if($scope.errors) //?: {has any}
				return

			//~: issue the request
			var p = jQuery.post('/login', {
				login: $scope.login, pass: $scope.pass
			})

			function failure()
			{
				$element.find('.btn-primary').velocity('callout.shake')
				$scope.$broadcast('content-login')
			}

			p.fail(failure).done(function(o)
			{
				if(!ZeT.iso(o) || !o.success)
					return failure()

				//~: display welcome
				$rootScope.$broadcast('content-hide')
				$rootScope.$broadcast('content-welcome')
			})
		}

		//~: field placeholder
		$scope.placeholder = function(f)
		{
			return !ZeT.get($scope, 'errors', f)?(''):
			  ZeTS.cat('Enter value for ', f, '!')
		}
	})
})
