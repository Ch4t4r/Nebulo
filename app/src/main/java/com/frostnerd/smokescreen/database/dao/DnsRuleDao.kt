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

    @Insert
    fun insert(dnsRule: DnsRule)

    @Query("DELETE FROM DnsRule WHERE importedFrom IS NULL")
    fun deleteAllUserRules()

    @Query("UPDATE DnsRule SET stagingType=1 WHERE importedFrom IS NOT NULL")
    fun markNonUserRulesForDeletion()

    @Query("DELETE FROM DnsRule WHERE stagingType=1")
    fun deleteMarkedRules()

    @Query("DELETE FROM DnsRule WHERE stagingType=2")
    fun deleteStagedRules()

    @Query("UPDATE DnsRule SET stagingType=NULL")
    fun commitStaging()

    @Insert
    fun insertAll(rules: Collection<DnsRule>)

    @Query("SELECT COUNT(*) FROM DnsRule")
    fun getCount(): Long

    @Query("SELECT COUNT(*) FROM DnsRule WHERE importedFrom IS NULL")
    fun getUserCount():Long

    @Query("SELECT COUNT(*) FROM DnsRule WHERE importedFrom IS NOT NULL")
    fun getNonUserCount(): Long

    @Query("SELECT CASE WHEN :type=28 THEN IFNULL(ipv6Target, target) ELSE target END FROM DnsRule WHERE host=:host AND (type = :type OR type=255) AND (importedFrom is NULL OR IFNULL((SELECT enabled FROM HostSource h WHERE h.id=importedFrom),0) = 1) AND (importedFrom IS NOT NULL OR :useUserRules=1) LIMIT 1")
    fun findRuleTarget(host: String, type: Record.TYPE, useUserRules:Boolean): String?

    @Query("DELETE FROM DnsRule WHERE importedFrom=:sourceId")
    fun deleteAllFromSource(sourceId: Long)

    @Query("SELECT * FROM DnsRule WHERE importedFrom IS NULL ORDER BY host LIMIT :limit OFFSET :offset")
    fun getAllUserRules(offset:Int, limit:Int):List<DnsRule>

    @Query("SELECT * FROM DnsRule WHERE importedFrom IS NULL ORDER BY host")
    fun getAllUserRules():List<DnsRule>

    @Query("SELECT * FROM DnsRule WHERE importedFrom IS NOT NULL ORDER BY host LIMIT :limit OFFSET :offset")
    fun getAllNonUserRules(offset:Int, limit:Int):List<DnsRule>

    @Delete
    fun remove(rule:DnsRule)

    @Query("SELECT COUNT(*) FROM DnsRule WHERE importedFrom=:hostSourceId")
    fun getCountForHostSource(hostSourceId:Long):Int
}