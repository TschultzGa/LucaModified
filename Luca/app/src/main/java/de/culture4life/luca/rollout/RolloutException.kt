package de.culture4life.luca.rollout

class RolloutException : IllegalStateException {
    constructor() : super("This feature is not yet available")
    constructor(id: String) : super("The $id feature is not yet available")
}
