/*
 * CODENVY CONFIDENTIAL
 * __________________
 * 
 *  [2012] - [2013] Codenvy, S.A. 
 *  All Rights Reserved.
 * 
 * NOTICE:  All information contained herein is, and remains
 * the property of Codenvy S.A. and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to Codenvy S.A.
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Codenvy S.A..
 */
 
(function(){

    define(["jquery","underscore","backbone"], function($,_,Backbone){

        var Workspace = Backbone.Model.extend({

        });

        var Workspaces = Backbone.Collection.extend({
            model : Workspace,
            parse : function(response){
                return _.map(_.filter(response, function(r){
                    return r.temporary===false;
                }), function(r){
                    return { name : r.name, id : r.id, accountId : r.accountId };
                });
            },
            fetch : function(options){
                var dfd = $.Deferred();
                $.when(Backbone.Collection.prototype.fetch.apply(this,options))
                    .done(_.bind(function(){
                        dfd.resolve(this.models);
                    },this))
                    .fail(_.bind(function(){
                        dfd.reject(this);
                    },this));
                return dfd.promise();
            }
        });

        return {
            getWorkspaces : function(accountId){
                var newWorkspaces = new Workspaces();
                newWorkspaces.url = "/api/workspace/find/account?id="+accountId;
                return newWorkspaces.fetch();
            },

            Workspace : Workspace,
            Workspaces : Workspaces
        };

    });

}());