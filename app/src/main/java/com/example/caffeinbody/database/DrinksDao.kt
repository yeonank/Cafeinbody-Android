package com.example.caffeinbody.database

import androidx.room.*

@Dao
interface DrinksDao {
    @Insert
    fun insert(drink: Drinks)

    @Insert
    fun insertAll(vararg drink: Drinks)

    @Update
    fun update(drink: Drinks)

    @Delete
    fun delete(drink: Drinks)

    @Query("select * from Drinks where madeBy like :something")
    fun selectDrinkMadeBy(something: String): List<Drinks>

    @Query("select count(*) from Drinks")
    fun selectCount(): Int

    @Query("select * from Drinks where drinkName like :something")
    fun selectDrinkName(something: String): List<Drinks>

    @Query("select * from Drinks where category like :something")
    fun selectDrinkCategory(something: String): List<Drinks>
}