import { reactive } from 'vue';
import { defineStore } from 'pinia';
import { SKILL_CATALOG } from '../data';

function clone(value) {
  return JSON.parse(JSON.stringify(value));
}

export const useSkillStore = defineStore('skill', () => {
  const skills = reactive(clone(SKILL_CATALOG));

  function getSkill(id) {
    return skills.find((skill) => skill.id === id);
  }

  function toggleSkill(id) {
    const skill = getSkill(id);
    if (!skill) return null;
    skill.status = skill.status === '启用' ? '停用' : '启用';
    return skill;
  }

  function saveSkill(skill, patch) {
    Object.assign(skill, patch);
  }

  function addSkill(skill) {
    skills.unshift(skill);
    return skill;
  }

  return { skills, getSkill, toggleSkill, saveSkill, addSkill };
});
