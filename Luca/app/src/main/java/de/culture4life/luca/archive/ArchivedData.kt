package de.culture4life.luca.archive

interface ArchivedData<T> {

    fun getData(): List<T>

    fun setData(data: List<T>)

}