package com.frostnerd.smokescreen.database.dao

import androidx.room.*
import com.frostnerd.smokescreen.database.converters.DnsTypeConverter
import com.frostnerd.smokescreen.database.entities.DnsRule
import org.minidns.record.Record

/*
 * Copyright (C) 2019 Daniel Wolf (Ch4t4r)
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * You can contact the developer at daniel.wolf@frostnerd.com.
 */
@Dao
@TypeConverters(DnsTypeConverter::class)
interface DnsRuleDao {

    @Update
    fun update(dnsRule: DnsRule)

    @Update(onConflict = OnConflictStrategy.IGNORE)
    fun updateIgnore(dnsRule: DnsRule):Int

    @Insert
    fun insert(dnsRule: DnsRule)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertIgnore(dnsRule: DnsRule):Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertWhitelist(dnsRule: DnsRule):Long

    @Query("DELETE FROM DnsRule WHERE importedFrom IS NULL")
    fun deleteAllUserRules()

    @Query("UPDATE DnsRule SET stagingType=1 WHERE importedFrom IS NOT NULL AND stagingType=0")
    fun markNonUserRulesForDeletion()

    @Query("UPDATE DnsRule SET stagingType=0 WHERE importedFrom=:hostSourceId AND stagingType=1")
    fun unstageRulesOfSource(hostSourceId:Long)

    @Query("DELETE FROM DnsRule WHERE stagingType=1")
    fun deleteMarkedRules()

    @Query("DELETE FROM DnsRule WHERE stagingType=2")
    fun deleteStagedRules()

    @Query("UPDATE OR IGNORE DnsRule SET stagingType=0 WHERE stagingType!=0")
    fun commitStaging()

    @Insert
    fun insertAll(rules: Collection<DnsRule>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAllIgnoreConflict(rules: Collection<DnsRule>)

    @Query("SELECT COUNT(*) FROM DnsRule")
    fun getCount(): Long

    @Query("SELECT COUNT(*) FROM DnsRule WHERE importedFrom is NULL OR IFNULL((SELECT enabled FROM HostSource h WHERE h.id=importedFrom),0) = 1")
    fun getActiveCount(): Long

    @Query("SELECT host FROM DnsRule WHERE type=255 AND isWildcard=0 AND target='' AND (importedFrom is NULL OR IFNULL((SELECT enabled FROM HostSource h WHERE h.id=importedFrom),0) = 1) ORDER BY RANDOM() LIMIT :count")
    fun getRandomNonWildcardWhitelistEntries(count:Int):List<String>

    @Query("SELECT COUNT(*) FROM DnsRule WHERE isWildcard=1 AND (importedFrom is NULL OR IFNULL((SELECT enabled FROM HostSource h WHERE h.id=importedFrom),0) = 1)")
    fun getActiveWildcardCount(): Long

    @Query("SELECT COUNT(*) FROM DnsRule WHERE isWildcard=1 AND target='' AND (importedFrom is NULL OR IFNULL((SELECT enabled FROM HostSource h WHERE h.id=importedFrom),0) = 1)")
    fun getActiveWildcardWhitelistCount(): Long

    @Query("SELECT COUNT(*) FROM DnsRule WHERE target='' AND (importedFrom is NULL OR IFNULL((SELECT enabled FROM HostSource h WHERE h.id=importedFrom),0) = 1)")
    fun getActiveWhitelistCount(): Long

    @Query("SELECT COUNT(*) FROM DnsRule WHERE stagingType=0")
    fun getNonStagedCount(): Long

    @Query("SELECT COUNT(*) FROM DnsRule WHERE importedFrom IS NULL")
    fun getUserCount():Long

    @Query("SELECT COUNT(*) FROM DnsRule WHERE importedFrom IS NOT NULL")
    fun getNonUserCount(): Long

    @Query("SELECT CASE WHEN :type=28 THEN IFNULL(ipv6Target, target) ELSE target END FROM DnsRule d1 WHERE d1.host=:host AND d1.target != '' AND (d1.type = :type OR d1.type=255) AND (d1.importedFrom is NULL OR IFNULL((SELECT enabled FROM HostSource h WHERE h.id=d1.importedFrom),0) = 1) AND (d1.importedFrom IS NOT NULL OR :useUserRules=1) AND (SELECT COUNT(*) FROM DnsRule d2 WHERE d2.target='' AND d2.host=:host AND (d2.type = :type OR d2.type=255) AND (d2.importedFrom IS NOT NULL OR :useUserRules=1) AND (importedFrom is NULL OR IFNULL((SELECT enabled FROM HostSource h WHERE h.id=importedFrom),0)))=0 AND isWildcard=0 LIMIT 1")
    fun findRuleTarget(host: String, type: Record.TYPE, useUserRules:Boolean): String?

    @Query("SELECT * FROM DnsRule where host=:host AND type=255 AND isWildcard=0 AND target='' AND (importedFrom IS NOT NULL OR :useUserRules=1) AND (importedFrom is NULL OR IFNULL((SELECT enabled FROM HostSource h WHERE h.id=importedFrom),0) = 1) LIMIT 1")
    fun findNonWildcardWhitelistEntry(host:String, useUserRules:Boolean):List<DnsRule>

    @Query("SELECT * FROM DnsRule d1 WHERE ((:includeWhitelistEntries=1 AND d1.target == '') OR (:includeNonWhitelistEntries=1 AND d1.target!='')) AND (d1.type = :type OR d1.type=255) AND (d1.importedFrom is NULL OR IFNULL((SELECT enabled FROM HostSource h WHERE h.id=d1.importedFrom),0) = 1) AND (d1.importedFrom IS NOT NULL OR :useUserRules=1) AND :host LIKE host AND isWildcard=1")
    fun findPossibleWildcardRuleTarget(host: String, type: Record.TYPE, useUserRules:Boolean, includeWhitelistEntries:Boolean, includeNonWhitelistEntries:Boolean):List<DnsRule>

    @Query("SELECT * FROM DnsRule d1 WHERE d1.host=:host AND d1.target != '' AND (d1.type = :type OR d1.type=255) AND (d1.importedFrom is NULL OR IFNULL((SELECT enabled FROM HostSource h WHERE h.id=d1.importedFrom),0) = 1) AND (d1.importedFrom IS NOT NULL OR :useUserRules=1) AND (SELECT COUNT(*) FROM DnsRule d2 WHERE d2.target='' AND d2.host=:host AND (d2.type = :type OR d2.type=255) AND (d2.importedFrom IS NOT NULL OR :useUserRules=1) AND (importedFrom is NULL OR IFNULL((SELECT enabled FROM HostSource h WHERE h.id=importedFrom),0)))=0 AND isWildcard=0 LIMIT 1")
    fun findRuleTargetEntity(host: String, type: Record.TYPE, useUserRules:Boolean):DnsRule?

    @Query("DELETE FROM DnsRule WHERE importedFrom=:sourceId")
    fun deleteAllFromSource(sourceId: Long)

    @Query("SELECT * FROM DnsRule WHERE importedFrom IS NULL ORDER BY host LIMIT :limit OFFSET :offset")
    fun getAllUserRules(offset:Int, limit:Int):List<DnsRule>

    @Query("SELECT * FROM DnsRule WHERE importedFrom IS NULL ORDER BY host")
    fun getAllUserRules():List<DnsRule>

    @Query("SELECT * FROM DnsRule WHERE importedFrom IS NULL AND target != '' ORDER BY host")
    fun getAllUserRulesWithoutWhitelist():List<DnsRule>

    @Query("SELECT * FROM DnsRule WHERE importedFrom IS NOT NULL ORDER BY host LIMIT :limit OFFSET :offset")
    fun getAllNonUserRules(offset:Int, limit:Int):List<DnsRule>

    @Query("SELECT * FROM DnsRule WHERE importedFrom IS NOT NULL AND target != '' ORDER BY host LIMIT :limit OFFSET :offset")
    fun getAllNonUserRulesWithoutWhitelist(offset:Int, limit:Int):List<DnsRule>

    @Delete
    fun remove(rule:DnsRule)

    @Query("SELECT COUNT(*) FROM DnsRule WHERE importedFrom=:hostSourceId AND stagingType=0")
    fun getCountForHostSource(hostSourceId:Long):Int

    @Query("SELECT * FROM DnsRule WHERE importedFrom IS NOT NULL AND host=:host AND type=:type AND stagingType=2 LIMIT 1")
    fun getNonUserRule(host:String, type:Record.TYPE):DnsRule?

}