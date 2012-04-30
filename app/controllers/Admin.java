package controllers;

import java.util.Iterator;
import java.util.List;

import models.Blacklist;
import models.CascadingDelete;
import models.User;

import org.mortbay.log.Log;

import play.Logger;
import play.modules.objectify.Datastore;
import play.mvc.Before;
import play.mvc.Controller;
import play.mvc.With;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

@With(Login.class)
public class Admin extends Controller {

    private static final int SEARCH_MAX_FETCH_SIZE = 20;
    private static final int BLACKLIST_MAX_FETCH_SIZE = 100;

    public static void searchUser(String query) {
        User user = Login.getLoggedInUser();

        boolean ranSearch = false;
        List<User> results = Lists.newArrayList();

        if (query != null) {
            String normalized = query.toLowerCase().trim();
            Log.info("User query: " + query + " Normalized: " + normalized);    
            
            if (!normalized.isEmpty()) {
                ranSearch = true;

                // 1. look up by user id
                User userById = null;
                try {
                    Long userId = Long.parseLong(normalized);
                    userById = User.findById(userId);
                    if (userById != null) {
                        results.add(userById);
                    }
                } catch (NumberFormatException e) {
                    // ignore. the query term is not a user id.
                }
                
                // 2. look up by user name
                Iterator<User> itr = Datastore
                    .query(User.class)
                    .filter("nameLower >=", normalized)
                    .filter("nameLower <=", normalized +"\ufffd")
                    .limit(SEARCH_MAX_FETCH_SIZE)
                    .fetch().iterator();
                while (itr.hasNext()) {
                    User userByName = itr.next();
                    // dedup
                    if (userById == null || userById.id != userByName.id) {
                        results.add(userByName);
                    }
                }
            }
        }

        render(user, query, ranSearch, results);
    }

    public static void blacklistedUsers() {
        User user = Login.getLoggedInUser();
        
        List<Blacklist> blacklist = Lists.newArrayList();
        Iterator<Blacklist> itr = Datastore
            .query(Blacklist.class)
            .limit(BLACKLIST_MAX_FETCH_SIZE)
            .fetch().iterator();
        while (itr.hasNext()) {
            blacklist.add(itr.next());
        }

        render(user, blacklist);
    }

    public static void addToBlacklist(String userIdString) {
        User user = Login.getLoggedInUser();

        if (!User.isValidId(userIdString)) {
            flash.error("Invalid user id: " + userIdString);
            blacklistedUsers();

        } 
        
        long userId = Long.valueOf(userIdString);
        if (userId == user.id) {
            flash.error("Can't block self: " + userId);
            blacklistedUsers();
        }
        
        Blacklist blacklist = new Blacklist(userId);
        blacklist.save();
        
        flash.success("Blocked user id: " + userIdString);
        blacklistedUsers();
    }

    public static void removeFromBlacklist(String userIdString) {
        Preconditions.checkArgument(User.isValidId(userIdString),
                "Invalid user id: " + userIdString);
        
        long userId = Long.valueOf(userIdString);
        Blacklist blacklist = Blacklist.findById(userId);
        if (blacklist != null) {
            blacklist.delete();
        }
 
        flash.success("Unblocked user id: " + userIdString);
        blacklistedUsers();
    }

    public static void deleteUser() {
        User user = Login.getLoggedInUser();
        render(user);
    }

    public static void deleteUserPost(String userIdString) {
        checkAuthenticity();
        
        User user = Login.getLoggedInUser();

        if (!User.isValidId(userIdString)) {
            flash.error("Invalid user id: " + userIdString);
            deleteUser();
        }
        
        long userId = Long.valueOf(userIdString);
        
        // check the user is not currently logged in user
        if (user.id == userId) {
            flash.error("Can't delete self: %s", userIdString);
            deleteUser();
        }

        // check the user exists
        User userToDelete = User.findById(userId);
        if (userToDelete == null) {
            flash.error("Non-existant user: User id: %s", userIdString);
            deleteUser();
        }
        
        // delete the user
        CascadingDelete.delete(userToDelete);

        flash.success("Successfully deleted user: %s", userIdString);
        deleteUser();
    }

    @Before
    static void checkAccess() {
        User user = Login.getLoggedInUser();
        if (user == null || ! user.isAdmin()) {
            Logger.warn("Non-admin user attempted to access admin page: %s", user);
            forbidden("Must be admin.");
        }
    }

}