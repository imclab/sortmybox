package models;

import java.util.List;
import java.util.regex.Matcher;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

import controllers.RequiresLogin;

import play.Logger;

import models.Rule.RuleType;
import siena.Column;
import siena.Generator;
import siena.Id;
import siena.Model;
import siena.Query;

/**
 * 
 * @author mustpax
 * @author syyang
 */
public class Rule extends Model {

    public static enum RuleType {
        NAME_CONTAINS {
            @Override
            public boolean matches(String pattern, String fileName) {
                if ((pattern  == null) ||
                    (fileName == null)) {
                    return false;
                }

                return fileName.toLowerCase().contains(pattern.toLowerCase());
            }
        },
        GLOB {
            @Override
            public boolean matches(String pattern, String fileName) {
                Matcher m = RuleUtils.getGlobPattern(pattern).matcher(fileName);
                return m.matches();
            }
        },
        EXT_EQ {
            @Override 
            public boolean matches(String pattern, String fileName) {
                String ext = RuleUtils.getExtFromName(fileName);
                if ((ext == null) ||
                    (pattern == null)) {
                    return false;
                }

                return ext.toLowerCase().equals(pattern.toLowerCase());
            }
        };

        /**
         * Apply a match to a file based on the rule type.
         * 
         * @param pattern the pattern for the current rule
         * @param fileName file name to match against rule
         * @return true if given file name matches the current pattern
         */
        public abstract boolean matches(String pattern, String fileName);
    }

    @Id
    public Long id;

    public RuleType type; 

    public String pattern;

    public String dest;

    public Integer rank;

    public Long owner;

    public Rule() {}
    
    public Rule(RuleType type, String pattern, String dest, Integer rank, Long owner) {
        this.type = type;
        this.pattern = pattern;
        this.dest = dest;
        this.rank = rank;
        this.owner = owner;
    }

    public boolean matches(String fileName) {
        return type.matches(this.pattern, fileName);
    }

    public static Query<Rule> all() {
        return Model.all(Rule.class);
    }
    
    public static Rule findById(Long id) {
        return all().filter("id", id).get();
    }
    
    public static List<Rule> findByOwner(User owner) {
        Query<Rule> query = all();
        query.filter("owner", owner.id);
        query.order("rank");
        return query.fetch();
    }

    public static void insert(List<Rule> rules) {
        insert(RequiresLogin.getLoggedInUser(), rules);
    }

    public static void insert(User owner, List<Rule> rules) {
        if (rules == null || rules.isEmpty()) {
            return;
        }
        
        int rank = 0;
        for (Rule rule : rules) {
            rule.rank = rank++;
            rule.owner = owner.id;
        }
        Model.batch(Rule.class).insert(rules);
        Logger.info("Saved entities: %s:", rules);
    }

    @Override
    public int hashCode() {
        HashCodeBuilder hash = new HashCodeBuilder()
            .append(this.id)
            .append(this.type)
            .append(this.pattern)
            .append(this.dest)
            .append(this.rank)
            .append(this.owner);
        return hash.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        Rule other = (Rule) obj;
        EqualsBuilder eq = new EqualsBuilder()
            .append(this.id, other.id)
            .append(this.type, other.type)
            .append(this.pattern, other.pattern)
            .append(this.dest, other.dest)
            .append(this.rank, other.rank)
            .append(this.owner, other.owner);
        return eq.isEquals();
    }
    
    @Override
    public String toString() {
        return Objects.toStringHelper(User.class)
            .add("rule type", type)
            .add("pattern", pattern)
            .add("dest", dest)        
            .toString();
    }

}
